package som.interpreter.nodes;

import static som.vm.Symbols.symbolFor;

import java.util.List;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.primitives.nodes.PreevaluatedExpression;
import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.Symbols;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public class ResolvingImplicitReceiverSend extends AbstractMessageSendNode {

  private SSymbol                 selector;
  private final MethodScope       currentScope;
  private final MixinDefinitionId mixinId;
  private final VM                vm;

  /**
   * A helper field used to make sure we specialize this node only once,
   * because it gets removed, and races on the removal are very problematic.
   *
   * REM: acccess only under synchronized(this)!
   */
  private PreevaluatedExpression replacedBy;

  /**
   * In case this node becomes an outer send, we need to recalculate the
   * receiver also when the specialization was racy.
   *
   * REM: acccess only under synchronized(this)!
   */
  private OuterObjectRead newReceiverNodeForOuterSend;

  public ResolvingImplicitReceiverSend(final SSymbol selector,
      final ExpressionNode[] arguments, final MethodScope currentScope,
      final MixinDefinitionId mixinId, final VM vm) {
    super(arguments);
    this.selector = selector;
    this.currentScope = currentScope;
    this.mixinId = mixinId;
    this.vm = vm;
  }

  /**
   * For wrapped nodes only.
   */
  protected ResolvingImplicitReceiverSend() {
    this(null, null, null, null, null);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    return super.executeGeneric(frame);
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    // this specialize method is designed to be execute only once and
    // tracks its replacement nodes to avoid re-specialization in case of
    // re-execution
    PreevaluatedExpression newNode;
    Lock lock = getLock();
    try {
      lock.lock();
      newNode = specialize(frame, args);
    } finally {
      lock.unlock();
    }
    return newNode.doPreEvaluated(frame, args);
  }

  private PreevaluatedExpression reusePreviousSpecialization(final Object[] args) {
    PreevaluatedExpression newNode;
    OuterObjectRead newReceiverNode;
    synchronized (this) {
      newNode = replacedBy;
      newReceiverNode = newReceiverNodeForOuterSend;
    }

    // try to use the specialization that was done by another call to specialize
    if (newNode == null) {
      return null;
    }

    // has already been specialized
    // for outer sends, we still need to recalculate the real receiver
    if (newReceiverNode != null) {
      args[0] = newReceiverNode.computeOuter(args[0]);
    }
    return newNode;
  }

  private PreevaluatedExpression specialize(final VirtualFrame frame, final Object[] args) {
    PreevaluatedExpression newNode = reusePreviousSpecialization(args);
    if (newNode != null) {
      return newNode;
    }

    ExpressionNode[] msgArgNodes = argumentNodes; // for outer nodes we need to update them

    OuterObjectRead newOuterRead = null; // used to update newReceiverNodeForOuterSend

    // first check whether it is an outer send
    // it it is, we get the context level of the outer send and rewrite to one
    List<MixinDefinitionId> selectorResult = currentScope.lookupSlotOrClass(selector);
    List<MixinDefinitionId> dialectResult =
        currentScope.lookupSlotOrClass(Symbols.SECRET_DIALECT_SLOT);

    SClass selfClass = Types.getClassOf(args[0]);

    if (selectorResult != null && selectorResult.size() > 1) {
      assert mixinId == selectorResult.get(0);
      selectorResult.remove(0);

      msgArgNodes = argumentNodes.clone();
      ExpressionNode currentReceiver = msgArgNodes[0];

      MixinDefinitionId currentMixin = mixinId;

      for (MixinDefinitionId enclosingMixin : selectorResult) {
        currentReceiver =
            OuterObjectReadNodeGen.create(currentMixin, enclosingMixin, currentReceiver)
                                  .initialize(sourceSection);

        args[0] = ((OuterObjectRead) currentReceiver).executeEvaluated(args[0]);
        currentMixin = enclosingMixin;
      }

      msgArgNodes[0] = currentReceiver;
      newOuterRead = (OuterObjectRead) currentReceiver;

    } else if (!selfClass.canUnderstand(selector) && dialectResult != null) {
      assert mixinId == dialectResult.get(0);

      if (dialectResult.size() > 1) {
        dialectResult.remove(0);

        msgArgNodes = argumentNodes.clone();
        ExpressionNode currentReceiver = msgArgNodes[0];

        MixinDefinitionId currentMixin = mixinId;

        for (MixinDefinitionId enclosingMixin : dialectResult) {
          currentReceiver =
              OuterObjectReadNodeGen.create(currentMixin, enclosingMixin, currentReceiver)
                                    .initialize(sourceSection);

          args[0] = ((OuterObjectRead) currentReceiver).executeEvaluated(args[0]);
          currentMixin = enclosingMixin;
        }

        msgArgNodes[0] = currentReceiver;
        newOuterRead = (OuterObjectRead) currentReceiver;
      }

      // Expression to get dialect from module
      ExpressionNode dialectExpr =
          MessageSendNode.createMessageSend(Symbols.SECRET_DIALECT_SLOT,
              new ExpressionNode[] {msgArgNodes[0]},
              getSourceSection(), vm);
      insert(dialectExpr);
      args[0] = ((PreevaluatedExpression) dialectExpr).doPreEvaluated(frame, args);

      msgArgNodes[0] = dialectExpr;
    }

    ExpressionNode replacementNode =
        MessageSendNode.createMessageSend(selector, msgArgNodes, sourceSection, vm);

    synchronized (this) {
      if (newOuterRead != null) {
        newReceiverNodeForOuterSend = newOuterRead;
      }
      replacedBy = (PreevaluatedExpression) replacementNode;
    }

    return (PreevaluatedExpression) replace(replacementNode);
  }

  @Override
  public SSymbol getInvocationIdentifier() {
    return selector;
  }

  @Override
  public String toString() {
    return "ImplicitSend(" + selector.toString() + ")";
  }

  @Override
  public void addSuffixToSelector(final String suffix) {
    selector = symbolFor(selector.getString() + suffix);

  }
}
