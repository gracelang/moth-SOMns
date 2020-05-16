package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreter;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.inlining.ScopeAdaptationVisitor;
import bd.tools.nodes.Invocation;
import som.compiler.Variable.Local;
import som.vm.Symbols;
import som.vm.constants.Nil;
import som.vmobjects.Capability;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;


public abstract class NonLocalVariableNode extends ContextualNode
    implements Invocation<SSymbol> {

  protected final FrameSlot       slot;
  protected final FrameDescriptor descriptor;
  public final Local              var;

  // TODO: We currently assume that there is a 1:1 mapping between lexical contexts
  // and frame descriptors, which is apparently not strictly true anymore in Truffle 1.0.0.
  // Generally, we also need to revise everything in this area and address issue #240.
  private NonLocalVariableNode(final int contextLevel, final Local var) {
    super(contextLevel);
    this.slot = var.getSlot();
    this.descriptor = var.getFrameDescriptor();
    this.var = var;
  }

  public final Local getLocal() {
    return var;
  }

  @Override
  public final SSymbol getInvocationIdentifier() {
    return var.name;
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    if (tag == LocalVariableTag.class) {
      return true;
    } else {
      return super.hasTag(tag);
    }
  }

  public abstract static class NonLocalVariableReadNode extends NonLocalVariableNode {

    public NonLocalVariableReadNode(final int contextLevel, final Local var) {
      super(contextLevel, var);
    }

    public NonLocalVariableReadNode(final NonLocalVariableReadNode node) {
      this(node.contextLevel, node.var);
    }

    @Specialization(guards = "isUninitialized(frame)")
    public final Object doNil(final VirtualFrame frame) {
      return Nil.nilObject;
    }

    protected boolean isBoolean(final VirtualFrame frame) {
      return determineContext(frame).isBoolean(slot);
    }

    protected boolean isLong(final VirtualFrame frame) {
      return determineContext(frame).isLong(slot);
    }

    protected boolean isDouble(final VirtualFrame frame) {
      return determineContext(frame).isDouble(slot);
    }

    protected boolean isObject(final VirtualFrame frame) {
      return determineContext(frame).isObject(slot);
    }

    @Specialization(guards = {"isBoolean(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getBoolean(slot);
    }

    @Specialization(guards = {"isLong(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getLong(slot);
    }

    @Specialization(guards = {"isDouble(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getDouble(slot);
    }

    @Specialization(guards = {"isObject(frame)"},
        replaces = {"doBoolean", "doLong", "doDouble"},
        rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getObject(slot);
    }

    protected final boolean isUninitialized(final VirtualFrame frame) {
      return descriptor.getFrameSlotKind(slot) == FrameSlotKind.Illegal;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == LocalVarRead.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(var, this, contextLevel);
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class NonLocalVariableWriteNode extends NonLocalVariableNode {

    public NonLocalVariableWriteNode(final int contextLevel, final Local var) {
      super(contextLevel, var);
    }

    public NonLocalVariableWriteNode(final NonLocalVariableWriteNode node) {
      this(node.contextLevel, node.var);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind(frame)")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      determineContext(frame).setBoolean(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind(frame)")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      determineContext(frame).setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind(frame)")
    public final double writeDouble(final VirtualFrame frame, final double expValue) {
      determineContext(frame).setDouble(slot, expValue);
      return expValue;
    }

    @Specialization(replaces = {"writeBoolean", "writeLong", "writeDouble"})
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      ensureObjectKind();

      Object old = null;
      try {
        old = determineContext(frame).getObject(slot);
      } catch (FrameSlotTypeException e) {
        e.printStackTrace();
      }
      String error = null;
      if (expValue instanceof SAbstractObject) {
        if (((SAbstractObject) expValue).capability.equals(Capability.ALIASED_ISOLATE)) {
          error = "Attempted to store an Isolate that is still aliased";
        } else if (((SAbstractObject) expValue).capability.equals(Capability.ISOLATE)) {
          ((SAbstractObject) expValue).capability = Capability.ALIASED_ISOLATE;
        }
      }
      if (old instanceof SAbstractObject) {
        if (((SAbstractObject) old).capability.equals(Capability.ALIASED_ISOLATE)) {
          ((SAbstractObject) old).capability = Capability.ISOLATE;
        }
      }

      if (error != null) {
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

      determineContext(frame).setObject(slot, expValue);
      return old;
    }

    protected final boolean isBoolKind(final VirtualFrame frame) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Boolean) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeBoolToUninit");
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Boolean);
        return true;
      }
      return false;
    }

    protected final boolean isLongKind(final VirtualFrame frame) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Long) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeIntToUninit");
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Long);
        return true;
      }
      return false;
    }

    protected final boolean isDoubleKind(final VirtualFrame frame) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Double) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeDoubleToUninit");
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Double);
        return true;
      }
      return false;
    }

    protected final void ensureObjectKind() {
      descriptor.setFrameSlotKind(slot, FrameSlotKind.Object);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == LocalVarWrite.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(var, this, getExp(), contextLevel);
    }
  }
}
