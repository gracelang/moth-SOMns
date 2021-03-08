package som.interpreter.objectstorage;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageAccessor.AbstractPrimitiveAccessor;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.interpreter.objectstorage.StorageLocation.ObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vm.Symbols;
import som.vmobjects.Capability;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;


/**
 * Object initializer assignments are not handled as message sends.
 * Instead, we have do a direct write to avoid ambiguity that could be
 * introduced by a dynamic lookup.
 */
@NodeChildren({
    @NodeChild(value = "self", type = ExpressionNode.class),
    @NodeChild(value = "value", type = ExpressionNode.class)})
public abstract class InitializerFieldWrite extends ExprWithTagsNode {
  protected static final int LIMIT = 6;

  protected final SlotDefinition slot;

  protected InitializerFieldWrite(final SlotDefinition slot) {
    this.slot = slot;
  }

  protected final AbstractPrimitiveAccessor getLongAccessor(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof LongStorageLocation) {
      return (AbstractPrimitiveAccessor) loc.getAccessor();
    }
    return null;
  }

  protected final AbstractPrimitiveAccessor getDoubleAccessor(
      final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof DoubleStorageLocation) {
      return (AbstractPrimitiveAccessor) loc.getAccessor();
    }
    return null;
  }

  protected final AbstractObjectAccessor getObjectAccessor(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof ObjectStorageLocation) {
      return (AbstractObjectAccessor) loc.getAccessor();
    }
    return null;
  }

  protected final StorageLocation getUnwritten(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof UnwrittenStorageLocation) {
      return loc;
    }
    return null;
  }

  protected final StorageLocation getUnwrittenOrPrimitiveLocation(
      final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof UnwrittenStorageLocation ||
        loc instanceof LongStorageLocation ||
        loc instanceof DoubleStorageLocation) {
      return cachedLayout.getStorageLocation(slot);
    }
    return null;
  }

  protected final IntValueProfile createProfile() {
    return IntValueProfile.createIdentityProfile();
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {
          "accessor != null",
          "cachedLayout == rcvr.getObjectLayout()",
          "accessor.isPrimitiveSet(rcvr, primMarkProfile)"},
      limit = "LIMIT")
  public final long longValueSet(final SImmutableObject rcvr, final long value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {
          "accessor != null",
          "cachedLayout == rcvr.getObjectLayout()",
          "accessor.isPrimitiveSet(rcvr, primMarkProfile)"},
      limit = "LIMIT")
  public final long longValueSet(final SMutableObject rcvr, final long value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      replaces = "longValueSet",
      limit = "LIMIT")
  public final long longValueSetOrUnset(final SImmutableObject rcvr, final long value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    accessor.markPrimAsSet(rcvr, primMarkProfile);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      replaces = "longValueSet",
      limit = "LIMIT")
  public final long longValueSetOrUnset(final SMutableObject rcvr, final long value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    accessor.markPrimAsSet(rcvr, primMarkProfile);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()",
          "accessor.isPrimitiveSet(rcvr, primMarkProfile)"},
      limit = "LIMIT")
  public final double doubleValueSet(final SMutableObject rcvr, final double value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()",
          "accessor.isPrimitiveSet(rcvr, primMarkProfile)"},
      limit = "LIMIT")
  public final double doubleValueSet(final SImmutableObject rcvr, final double value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      replaces = "doubleValueSet",
      limit = "LIMIT")
  public final double doubleValueSetOrUnset(final SMutableObject rcvr, final double value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    accessor.markPrimAsSet(rcvr, primMarkProfile);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      replaces = "doubleValueSet",
      limit = "LIMIT")
  public final double doubleValueSetOrUnset(final SImmutableObject rcvr, final double value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleAccessor(cachedLayout)") final AbstractPrimitiveAccessor accessor) {
    accessor.write(rcvr, value);
    accessor.markPrimAsSet(rcvr, primMarkProfile);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      limit = "LIMIT")
  public final Object objectValue(final SImmutableObject rcvr, final Object value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getObjectAccessor(cachedLayout)") final AbstractObjectAccessor accessor) {

    String error = null;
    if (value instanceof SAbstractObject) {
      if (((SAbstractObject) value).capability.equals(Capability.ALIASED_ISOLATE)) {
        error = "Attempted to store an Isolate that is still aliased";
      } else if (((SAbstractObject) value).capability.equals(Capability.ISOLATE)) {
        ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
      }
    }
    if (error != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // Get the human-readable version of the source location
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

      // Throw the exception
      ExceptionSignalingNode exNode =
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection);
      insert(exNode);
      exNode.signal(suffix + error);
    }

    accessor.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"accessor != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      limit = "LIMIT")
  public final Object objectValue(final SMutableObject rcvr, final Object value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getObjectAccessor(cachedLayout)") final AbstractObjectAccessor accessor) {
    String error = null;
    if (value instanceof SAbstractObject) {
      if (((SAbstractObject) value).capability.equals(Capability.ALIASED_ISOLATE)) {
        error = "Attempted to store an Isolate that is still aliased";
      } else if (((SAbstractObject) value).capability.equals(Capability.ISOLATE)) {
        ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
      }
    }
    if (error != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // Get the human-readable version of the source location
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

      // Throw the exception
      ExceptionSignalingNode exNode =
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection);
      insert(exNode);
      exNode.signal(suffix + error);
    }

    accessor.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"location != null",
          "cachedLayout == rcvr.getObjectLayout()"},
      limit = "LIMIT")
  public final Object unwritten(final SObject rcvr, final Object value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getUnwritten(cachedLayout)") final StorageLocation location) {
    CompilerDirectives.transferToInterpreter();
    String error = null;
    if (value instanceof SAbstractObject) {
      if (((SAbstractObject) value).capability.equals(Capability.ALIASED_ISOLATE)) {
        error = "Attempted to store an Isolate that is still aliased";
      } else if (((SAbstractObject) value).capability.equals(Capability.ISOLATE)) {
        ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
      }
    }
    if (error != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // Get the human-readable version of the source location
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

      // Throw the exception
      ExceptionSignalingNode exNode =
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection);
      insert(exNode);
      exNode.signal(suffix + error);
    }

    ObjectTransitionSafepoint.INSTANCE.writeUninitializedSlot(rcvr, slot, value);
    return value;
  }

  public abstract Object executeEvaluated(SObject rcvr, Object value);

  @Specialization(guards = {"!rcvr.getObjectLayout().isValid()"})
  public final Object updateObject(final SObject rcvr, final Object value) {
    // no invalidation, just moving to interpreter to avoid recursion in PE
    CompilerDirectives.transferToInterpreter();
    String error = null;
    if (value instanceof SAbstractObject) {
      if (((SAbstractObject) value).capability.equals(Capability.ALIASED_ISOLATE)) {
        error = "Attempted to store an Isolate that is still aliased";
      } else if (((SAbstractObject) value).capability.equals(Capability.ISOLATE)) {
        ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
      }
    }
    if (error != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // Get the human-readable version of the source location
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

      // Throw the exception
      ExceptionSignalingNode exNode =
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection);
      insert(exNode);
      exNode.signal(suffix + error);
    }

    ObjectTransitionSafepoint.INSTANCE.transitionObject(rcvr);
    return executeEvaluated(rcvr, value);
  }

  @TruffleBoundary
  protected boolean isNonObjectLocation(final SObject rcvr) {
    return !(rcvr.getObjectLayout().getStorageLocation(slot) instanceof ObjectStorageLocation);
  }

  @Specialization(guards = {"isNonObjectLocation(rcvr)"})
  public final Object generalizeStorageLocation(final SObject rcvr, final Object value) {
    CompilerDirectives.transferToInterpreter();
    String error = null;
    if (value instanceof SAbstractObject) {
      if (((SAbstractObject) value).capability.equals(Capability.ALIASED_ISOLATE)) {
        error = "Attempted to store an Isolate that is still aliased";
      } else if (((SAbstractObject) value).capability.equals(Capability.ISOLATE)) {
        ((SAbstractObject) value).capability = Capability.ALIASED_ISOLATE;
      }
    }
    if (error != null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // Get the human-readable version of the source location
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "] ";

      // Throw the exception
      ExceptionSignalingNode exNode =
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection);
      insert(exNode);
      exNode.signal(suffix + error);
    }

    rcvr.writeSlot(slot, value);
    return value;
  }
}
