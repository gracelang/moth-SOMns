package som.interpreter.nodes;

import com.oracle.truffle.api.CompilerDirectives;
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
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.Symbols;
import som.vm.constants.Nil;
import som.vmobjects.Capability;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;


public abstract class LocalVariableNode extends ExprWithTagsNode
    implements Invocation<SSymbol> {

  protected final FrameSlot       slot;
  protected final FrameDescriptor descriptor;
  public final Local              var;

  // TODO: We currently assume that there is a 1:1 mapping between lexical contexts
  // and frame descriptors, which is apparently not strictly true anymore in Truffle 1.0.0.
  // Generally, we also need to revise everything in this area and address issue #240.
  private LocalVariableNode(final Local var) {
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

  public abstract static class LocalVariableReadNode extends LocalVariableNode {

    public LocalVariableReadNode(final Local variable) {
      super(variable);
    }

    public LocalVariableReadNode(final LocalVariableReadNode node) {
      this(node.var);
    }

    @Specialization(guards = "isUninitialized(frame)")
    public final Object doNil(final VirtualFrame frame) {
      return Nil.nilObject;
    }

    protected boolean isBoolean(final VirtualFrame frame) {
      return frame.isBoolean(slot);
    }

    protected boolean isLong(final VirtualFrame frame) {
      return frame.isLong(slot);
    }

    protected boolean isDouble(final VirtualFrame frame) {
      return frame.isDouble(slot);
    }

    protected boolean isObject(final VirtualFrame frame) {
      return frame.isObject(slot);
    }

    @Specialization(guards = {"isBoolean(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      boolean ret = frame.getBoolean(slot);
      return ret;
    }

    @Specialization(guards = {"isLong(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      long ret = frame.getLong(slot);
      return ret;
    }

    @Specialization(guards = {"isDouble(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      double ret = frame.getDouble(slot);
      return ret;
    }

    @Specialization(guards = {"isObject(frame)"},
        replaces = {"doBoolean", "doLong", "doDouble"},
        rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame)
        throws FrameSlotTypeException, IllegalArgumentException {
      Object ret = frame.getObject(slot);
      return ret;
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
    public String toString() {
      return this.getClass().getSimpleName() + "[" + var.name + "]";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(var, this, 0);
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class LocalVariableWriteNode extends LocalVariableNode {

    public LocalVariableWriteNode(final Local variable) {
      super(variable);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node) {
      super(node.var);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind(expValue)")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      frame.setBoolean(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind(expValue)")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      frame.setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind(expValue)")
    public final double writeDouble(final VirtualFrame frame, final double expValue) {
      frame.setDouble(slot, expValue);
      return expValue;
    }

    @Specialization(replaces = {"writeBoolean", "writeLong", "writeDouble"})
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      slot.setKind(FrameSlotKind.Object);
      Object old = null;
      try {
        old = frame.getObject(slot);
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
      frame.setObject(slot, expValue);
      return old;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isBoolKind(final boolean expValue) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Boolean) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Boolean);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isLongKind(final long expValue) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Long) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Long);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isDoubleKind(final double expValue) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Double) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Double);
        return true;
      }
      return false;
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
    public String toString() {
      return this.getClass().getSimpleName() + "[" + var.name + "]";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(var, this, getExp(), 0);
    }
  }
}
