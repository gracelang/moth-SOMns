package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageAccessor.AbstractPrimitiveAccessor;
import som.vm.constants.Nil;
import som.vmobjects.Capability;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SMutableObject;
import tools.dym.Tags.FieldWrite;


/**
 * <code>CachedSlotWrite</code> mirrors the functionality of
 * {@link CachedSlotRead} and embeds the field writing operations into a
 * dispatch chain. See {@link CachedSlotRead} for more details on the design.
 */
@GenerateWrapper
public abstract class CachedSlotWrite extends AbstractDispatchNode {
  @Child protected AbstractDispatchNode nextInCache;

  protected final CheckSObject guardForRcvr;

  public CachedSlotWrite(final CheckSObject guardForRcvr,
      final AbstractDispatchNode nextInCache) {
    super(nextInCache.getSourceSection());
    this.guardForRcvr = guardForRcvr;
    this.nextInCache = nextInCache;
  }

  /**
   * For wrapped nodes only.
   */
  protected CachedSlotWrite() {
    super(null);
    this.guardForRcvr = null;
  }

  public abstract Object doWrite(SObject obj, Object value);

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    try {
      if (guardForRcvr.entryMatches(arguments[0], null)) {
        return doWrite((SMutableObject) arguments[0], arguments[1]);
        // return arguments[1];
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(frame, arguments);
    }
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    return tag == FieldWrite.class;
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    if (getParent() instanceof ClassSlotAccessNode) {
      return new CachedSlotWriteWrapper(this, probe);
    } else {
      return new AbstractDispatchNodeWrapper(this, probe);
    }
  }

  public static final class UnwrittenSlotWrite extends CachedSlotWrite {
    private final SlotDefinition slot;

    public UnwrittenSlotWrite(final SlotDefinition slot, final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(guardForRcvr, nextInCache);
      this.slot = slot;
    }

    @Override
    public Object doWrite(final SObject obj, final Object value) {
      TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
      String error = null;
      Capability vc = null;
      if (value instanceof SAbstractObject) {
        vc = ((SAbstractObject) value).capability;
        if (((SAbstractObject) value).capability.equals(Capability.ALIASED_ISOLATE)) {
          error = "Attempted to store an Isolate that is still aliased";
        } else if (vc.equals(Capability.ISOLATE)) {
          ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
        }
      }
      if (error == null && vc != null && !obj.capability.supports(vc)) {
        error = obj.capability + " doesn't support field values of " + vc;
      }

      if (error != null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        // Get the human-readable version of the source location
        int line = sourceSection.getStartLine();
        int column = sourceSection.getStartColumn();
        String[] parts = sourceSection.getSource().getURI().getPath().split("/");
        String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

        throw new RuntimeException(suffix + error);
        // ExceptionsPrims
        // Throw the exception
        // ExceptionSignalingNode.createNode(
        // Symbols.symbolFor("TypeError"), sourceSection).signal(
        // suffix + error);
      }

      ObjectTransitionSafepoint.INSTANCE.writeUninitializedSlot(obj, slot, value);

      return Nil.nilObject;
    }
  }

  public static final class ObjectSlotWrite extends CachedSlotWrite {
    private final AbstractObjectAccessor accessor;

    public ObjectSlotWrite(final AbstractObjectAccessor accessor,
        final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(guardForRcvr, nextInCache);
      this.accessor = accessor;
    }

    @Override
    public Object doWrite(final SObject obj, final Object value) {
      Object old = accessor.read(obj);

      String error = null;
      Capability vc = null;
      if (value instanceof SAbstractObject) {
        vc = ((SAbstractObject) value).capability;
        if (vc.equals(Capability.ALIASED_ISOLATE)) {
          error = "Attempted to store an Isolate that is still aliased";
        } else if (vc.equals(Capability.ISOLATE)) {
          ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
        }
      }
      if (old instanceof SAbstractObject) {
        if (Capability.ALIASED_ISOLATE.equals(((SAbstractObject) old).capability)) {
          ((SAbstractObject) old).capability = Capability.ISOLATE;
        }
      }

      if (error == null && vc != null && !obj.capability.supports(vc)) {
        error = obj.capability + " doesn't support field values of " + vc;
      }

      if (error == null && obj.capability == Capability.IMMUTABLE) {
        error = obj.capability + " doesn't support field writes as it is marked as Immutable";
      }

      if (error != null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        // Get the human-readable version of the source location
        int line = sourceSection.getStartLine();
        int column = sourceSection.getStartColumn();
        String[] parts = sourceSection.getSource().getURI().getPath().split("/");
        String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

        throw new RuntimeException(suffix + error);
        // Throw the exception
        // ExceptionSignalingNode.createNode(
        // Symbols.symbolFor("TypeError"), sourceSection).signal(
        // suffix + error);
      }

      accessor.write(obj, value);
      return old;
    }
  }

  private abstract static class PrimSlotWrite extends CachedSlotWrite {
    protected final AbstractPrimitiveAccessor accessor;
    protected final SlotDefinition            slot;
    protected final IntValueProfile           primMarkProfile;

    PrimSlotWrite(final SlotDefinition slot, final AbstractPrimitiveAccessor accessor,
        final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(guardForRcvr, nextInCache);
      this.accessor = accessor;
      this.slot = slot;
      this.primMarkProfile = IntValueProfile.createIdentityProfile();
    }
  }

  public static final class LongSlotWriteSetOrUnset extends PrimSlotWrite {

    public LongSlotWriteSetOrUnset(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guardForRcvr, nextInCache);
    }

    @Override
    public Object doWrite(final SObject obj, final Object value) {
      long old = accessor.readLong(obj);
      if (value instanceof Long) {
        accessor.write(obj, (long) value);
        accessor.markPrimAsSet(obj, primMarkProfile);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
      return old;
    }
  }

  public static final class LongSlotWriteSet extends PrimSlotWrite {

    public LongSlotWriteSet(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guardForRcvr, nextInCache);
    }

    @Override
    public Object doWrite(final SObject obj, final Object value) {
      long old = accessor.readLong(obj);
      if (value instanceof Long) {
        accessor.write(obj, (long) value);
        if (!accessor.isPrimitiveSet(obj, primMarkProfile)) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          accessor.markPrimAsSet(obj);

          // fall back to LongSlotWriteSetOrUnset
          replace(new LongSlotWriteSetOrUnset(slot, accessor, guardForRcvr,
              nextInCache));
        }
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
      return old;
    }
  }

  public static final class DoubleSlotWriteSetOrUnset extends PrimSlotWrite {

    public DoubleSlotWriteSetOrUnset(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guardForRcvr, nextInCache);
    }

    @Override
    public Object doWrite(final SObject obj, final Object value) {
      double old = accessor.readDouble(obj);
      if (value instanceof Double) {
        accessor.write(obj, (double) value);
        accessor.markPrimAsSet(obj, primMarkProfile);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
      return old;
    }
  }

  public static final class DoubleSlotWriteSet extends PrimSlotWrite {

    public DoubleSlotWriteSet(final SlotDefinition slot,
        final AbstractPrimitiveAccessor accessor, final CheckSObject guardForRcvr,
        final AbstractDispatchNode nextInCache) {
      super(slot, accessor, guardForRcvr, nextInCache);
    }

    @Override
    public Object doWrite(final SObject obj, final Object value) {
      double old = accessor.readDouble(obj);
      if (value instanceof Double) {
        accessor.write(obj, (double) value);
        if (!accessor.isPrimitiveSet(obj, primMarkProfile)) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          accessor.markPrimAsSet(obj);

          // fall back to LongSlotWriteSetOrUnset
          replace(new DoubleSlotWriteSetOrUnset(slot, accessor, guardForRcvr,
              nextInCache));
        }
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        ObjectTransitionSafepoint.INSTANCE.writeAndGeneralizeSlot(obj, slot, value);
      }
      return old;
    }
  }
}
