package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import bd.inlining.ScopeAdaptationVisitor;
import bd.tools.nodes.Invocation;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.Variable.AccessNodeState;
import som.compiler.Variable.Argument;
import som.interpreter.SArguments;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.Symbols;
import som.vm.constants.Nil;
import som.vmobjects.Capability;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;
import tools.debugger.Tags.ArgumentTag;
import tools.debugger.Tags.KeywordTag;
import tools.dym.Tags.LocalArgRead;


public abstract class ArgumentReadNode {

  @GenerateWrapper
  public static class LocalArgumentReadNode extends ExprWithTagsNode
      implements Invocation<SSymbol> {
    protected final int      argumentIndex;
    protected final Argument arg;

    public LocalArgumentReadNode(final Argument arg) {
      assert arg.index > 0 ||
          this instanceof LocalSelfReadNode ||
          this instanceof LocalSuperReadNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
    }

    /** For Wrapper use only. */
    protected LocalArgumentReadNode() {
      this.argumentIndex = 0;
      this.arg = null;
    }

    /** For use in primitives only. */
    public LocalArgumentReadNode(final boolean insidePrim, final int argIdx) {
      this.argumentIndex = argIdx;
      this.arg = null;
      assert insidePrim : "Only to be used for primitive nodes";
    }

    @Override
    public WrapperNode createWrapper(final ProbeNode probe) {
      return new LocalArgumentReadNodeWrapper(this, probe);
    }

    public Argument getArg() {
      return arg;
    }

    @Override
    public final SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(frame, argumentIndex);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == ArgumentTag.class) {
        return true;
      } else if (tag == LocalArgRead.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }

    @Override
    public String toString() {
      return "LocalArg(" + argumentIndex + ")";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }
  }

  @GenerateWrapper
  public static class LocalArgumentInitNode extends ExprWithTagsNode
      implements Invocation<SSymbol> {
    protected final int      argumentIndex;
    protected final Argument arg;

    public LocalArgumentInitNode(final Argument arg,
        final SourceSection sourceSection) {
      assert arg.index > 0 ||
          this instanceof LocalArgumentInitNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
      this.sourceSection = sourceSection;
    }

    /** For Wrapper use only. */
    protected LocalArgumentInitNode() {
      this.argumentIndex = 0;
      this.arg = null;
    }

    /** For use in primitives only. */
    public LocalArgumentInitNode(final boolean insidePrim, final int argIdx) {
      this.argumentIndex = argIdx;
      this.arg = null;
      assert insidePrim : "Only to be used for primitive nodes";
    }

    @Override
    public WrapperNode createWrapper(final ProbeNode probe) {
      return new LocalArgumentInitNodeWrapper(this, probe);
    }

    public Argument getArg() {
      return arg;
    }

    @Override
    public final SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] args = frame.getArguments();
      Object obj = args[argumentIndex];
      String error = null;
      if (obj instanceof SAbstractObject) {
        if (((SAbstractObject) obj).capability.equals(Capability.ALIASED_ISOLATE)) {
          error = "Attempted to store an Isolate that is still aliased";
        } else if (((SAbstractObject) obj).capability.equals(Capability.ISOLATE)) {
          ((SAbstractObject) obj).capability = Capability.ALIASED_ISOLATE;
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
      return obj;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == ArgumentTag.class) {
        return true;
      } else if (tag == LocalArgRead.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }

    @Override
    public String toString() {
      return "LocalInitArg(" + argumentIndex + ")";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }
  }

  @GenerateWrapper
  public static class LocalArgumentDestructiveReadNode extends ExprWithTagsNode
      implements Invocation<SSymbol> {
    protected final int      argumentIndex;
    protected final Argument arg;

    public LocalArgumentDestructiveReadNode(final Argument arg,
        final SourceSection sourceSection) {
      assert arg.index > 0 ||
          this instanceof LocalArgumentDestructiveReadNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
      this.sourceSection = sourceSection;
    }

    /** For Wrapper use only. */
    protected LocalArgumentDestructiveReadNode() {
      this.argumentIndex = 0;
      this.arg = null;
    }

    /** For use in primitives only. */
    public LocalArgumentDestructiveReadNode(final boolean insidePrim, final int argIdx) {
      this.argumentIndex = argIdx;
      this.arg = null;
      assert insidePrim : "Only to be used for primitive nodes";
    }

    @Override
    public WrapperNode createWrapper(final ProbeNode probe) {
      return new LocalArgumentDestructiveReadNodeWrapper(this, probe);
    }

    public Argument getArg() {
      return arg;
    }

    @Override
    public final SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] args = frame.getArguments();
      Object obj = args[argumentIndex];
      args[argumentIndex] = Nil.nilObject;
      if (obj instanceof SAbstractObject) {
        if (((SAbstractObject) obj).capability.equals(Capability.ALIASED_ISOLATE)) {
          ((SAbstractObject) obj).capability = Capability.ISOLATE;
        }
      }
      return obj;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == ArgumentTag.class) {
        return true;
      } else if (tag == LocalArgRead.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }

    @Override
    public String toString() {
      return "LocalDestructiveArg(" + argumentIndex + ")";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }
  }

  public static class LocalSelfReadNode extends LocalArgumentReadNode implements ISpecialSend {

    private final MixinDefinitionId mixin;
    private final ValueProfile      rcvrClass = ValueProfile.createClassProfile();

    public LocalSelfReadNode(final Argument arg, final MixinDefinitionId mixin) {
      super(arg);
      this.mixin = mixin;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(frame));
    }

    @Override
    public boolean isSuperSend() {
      return false;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return mixin;
    }

    @Override
    public String toString() {
      return "LocalSelf";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateThisRead(arg, this, new AccessNodeState(mixin), 0);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }
  }

  public static class NonLocalArgumentReadNode extends ContextualNode
      implements Invocation<SSymbol> {
    protected final int      argumentIndex;
    protected final Argument arg;

    public NonLocalArgumentReadNode(final Argument arg, final int contextLevel) {
      super(contextLevel);
      assert contextLevel > 0;
      assert arg.index > 0 ||
          this instanceof NonLocalSelfReadNode ||
          this instanceof NonLocalSuperReadNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
    }

    public final Argument getArg() {
      return arg;
    }

    @Override
    public final SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(determineContext(frame), argumentIndex);
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, contextLevel);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == ArgumentTag.class) {
        return true;
      } else if (tag == LocalArgRead.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }
  }

  public static final class NonLocalSelfReadNode
      extends NonLocalArgumentReadNode implements ISpecialSend {
    private final MixinDefinitionId mixin;

    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();

    public NonLocalSelfReadNode(final Argument arg, final MixinDefinitionId mixin,
        final int contextLevel) {
      super(arg, contextLevel);
      this.mixin = mixin;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(determineContext(frame)));
    }

    @Override
    public boolean isSuperSend() {
      return false;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return mixin;
    }

    @Override
    public String toString() {
      return "NonLocalSelf";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateThisRead(arg, this, new AccessNodeState(mixin), contextLevel);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }
  }

  public static final class LocalSuperReadNode extends LocalArgumentReadNode
      implements ISuperReadNode {

    private final MixinDefinitionId holderMixin;
    private final boolean           classSide;

    public LocalSuperReadNode(final Argument arg, final MixinDefinitionId holderMixin,
        final boolean classSide) {
      super(arg);
      this.holderMixin = holderMixin;
      this.classSide = classSide;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return holderMixin;
    }

    @Override
    public boolean isClassSide() {
      return classSide;
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateSuperRead(arg, this, new AccessNodeState(holderMixin, classSide), 0);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }
  }

  public static final class NonLocalSuperReadNode extends
      NonLocalArgumentReadNode implements ISuperReadNode {

    private final MixinDefinitionId holderMixin;
    private final boolean           classSide;

    public NonLocalSuperReadNode(final Argument arg, final int contextLevel,
        final MixinDefinitionId holderMixin, final boolean classSide) {
      super(arg, contextLevel);
      this.holderMixin = holderMixin;
      this.classSide = classSide;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return holderMixin;
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateSuperRead(arg, this, new AccessNodeState(holderMixin, classSide),
          contextLevel);
    }

    @Override
    public boolean isClassSide() {
      return classSide;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }
  }
}
