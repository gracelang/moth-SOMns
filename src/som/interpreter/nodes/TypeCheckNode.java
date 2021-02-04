package som.interpreter.nodes;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.SomException;
import som.interpreter.Types;
import som.interpreter.nodes.TypeCheckNodeFactory.BooleanTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.BrandTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.CustomTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.DoubleTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.LongTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.NonPrimitiveTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.SArrayTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.SBlockTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.SSymbolTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.StringTypeCheckNodeFactory;
import som.interpreter.nodes.TypeCheckNodeFactory.UnresolvedTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import som.vmobjects.SType;
import som.vmobjects.SType.BrandType;
import som.vmobjects.SType.InterfaceType;


public abstract class TypeCheckNode extends BinaryExpressionNode {
  /**
   * The following four fields used for the collection of type checking stats.
   */
  public static long numTypeCheckExecutions;
  public static long numSubclassChecks;
  public static int  numTypeCheckLocations;
  public static int  nTypes;

  /**
   * The following three constanst act as the enum values for the super type array.
   */
  public static final byte MISSING = 0;
  public static final byte SUBTYPE = 1;
  public static final byte FAIL    = 2;

  /**
   * Reports the stats collected if the setting to is turned on.
   */
  public static void reportStats() {
    if (!VmSettings.COLLECT_TYPE_STATS) {
      return;
    }
    Output.println("RESULT-NumberOfTypeCheckExecutions: " + numTypeCheckExecutions);
    Output.println("RESULT-NumberOfSubclassChecks: " + numSubclassChecks);
    Output.println("RESULT-NumberOfTypes: " + nTypes);
  }

  /**
   * The cache of whether one type is a supertype to another for custom type objects. A type is
   * a super type, if the inner map stores true for the subtype.
   */
  private static final EconomicMap<Object, EconomicMap<Object, Boolean>> isSuperclassTable =
      VmSettings.USE_SUBTYPE_TABLE ? EconomicMap.create() : null;
  /**
   * The cache of whether one type is a supertype to another for builtin types. A type is a
   * super type, if the inner map stores true for the subtype.
   */
  private static final byte[][]                                          isSuperclassArray =
      VmSettings.USE_SUBTYPE_TABLE ? new byte[1000][1000] : null;

  /**
   * Creates a type check.
   *
   * @param type - the type expression to check objects as
   * @param expr - the expression to be type checked
   * @param sourceSection - the location in the source
   * @return The wrapped expression
   */
  public static ExpressionNode create(final ExpressionNode type, final ExpressionNode expr,
      final SourceSection sourceSection) {
    // Only create the type check if type checking is enabled
    if (VmSettings.USE_TYPE_CHECKING) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckLocations;
      }
      // Create the type check
      return UnresolvedTypeCheckNodeFactory.create(sourceSection, type, expr);
    }
    // Otherwise return the expression as is.
    return expr;
  }

  /**
   * Marker of whether an expression is a type check. Useful as not all type checks share the
   * same superclasses.
   */
  public interface ATypeCheckNode {
  }

  /**
   * The common superclass for type checks that already have a resolved type.
   */
  protected abstract static class UnaryTypeCheckingNode extends UnaryExpressionNode
      implements ATypeCheckNode {

    /**
     * Throw a type exception in the execution of the code.
     *
     * @param argument - the object being type checked
     * @param type - the type of the argument
     * @param expected - the expected type
     * @param sourceSection - the location at which the error occurred
     * @param exception - the exception to use to raise the error
     */
    protected void throwTypeError(final Object argument, final Object type,
        final Object expected, final SourceSection sourceSection,
        ExceptionSignalingNode exception) {
      CompilerDirectives.transferToInterpreter();

      // Create the exception node if it hasn't been already
      if (exception == null) {
        ExceptionSignalingNode exNode = ExceptionSignalingNode.createNode(
            Symbols.symbolFor("TypeError"), sourceSection);
        insert(exNode);
        exception = exNode;
      }

      // Get the human-readable version of the source location
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";

      // Throw the exception
      exception.signal(suffix + " \"" + argument + "\" is not a subtype of "
          + sourceSection.getCharacters() + ", because it has the type: \n" + type
          + "\n    when it was expected to have type: \n" + expected);
    }
  }

  /**
   * Creates the node used to throw exceptions.
   *
   * @param ss - the source location of the type check
   * @return The exception node.
   */
  protected static ExceptionSignalingNode createExceptionNode(final SourceSection ss) {
    CompilerDirectives.transferToInterpreter();
    return ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), ss);
  }

  /**
   * Common methods shared by type checking nodes.
   */
  protected interface TypeCheckingNode {
    /**
     * Checks whether an object is known to be subtype in the cache.
     *
     * @param isSub - the cache of subtypes
     * @param expected - the expected type
     * @param argument - the object being checked
     * @param type - the type of the argument
     * @param sourceSection - the location in source of the type check
     * @param exception - the node used to throw errors
     * @return The argument if it is a subtype. Null if it wasn't in the cache. Otherwise an
     *         error is thrown.
     */
    default <E> E checkTable(final byte[] isSub,
        final SObjectWithClass expected, final E argument, final SType type,
        final SourceSection sourceSection, final ExceptionSignalingNode exception) {
      byte sub = isSub[type.id];
      if (sub == SUBTYPE) {
        return argument;
      } else if (sub == FAIL) {
        reportError(argument, type, expected, sourceSection, exception);
      }
      return null;
    }

    /**
     * The method that should call throwTypeError.
     */
    void reportError(Object argument, Object type, Object expected,
        SourceSection sourceSection, ExceptionSignalingNode exception);

    /**
     * Checks whether an object is Nil.
     */
    default boolean isNil(final SObjectWithoutFields obj) {
      return obj == Nil.nilObject;
    }
  }

  /**
   * The type check that has not evaluated the type yet. Replaces itself with a more specific
   * check based on the type.
   */
  @GenerateNodeFactory
  public abstract static class UnresolvedTypeCheckNode extends BinaryExpressionNode
      implements ATypeCheckNode {

    protected UnresolvedTypeCheckNode(final SourceSection sourceSection) {
      this.sourceSection = sourceSection;
    }

    @Specialization
    public Object executeEvaluated(final VirtualFrame frame, final SObjectWithClass expected,
        final Object argument) {
      // Find the expression being typed check
      ExpressionNode argumentExpr = null;
      for (Node node : this.getChildren()) {
        argumentExpr = (ExpressionNode) node;
      }

      // If the expected type is builtin
      if (expected instanceof SType) {
        // For brand types, replace with the brand check
        if (expected instanceof BrandType) {
          BrandTypeCheckNode brandNode =
              BrandTypeCheckNodeFactory.create((BrandType) expected, sourceSection,
                  argumentExpr);
          replace(brandNode);
          return brandNode.executeEvaluated(argument);
        }

        // Get the subtype cache
        byte[] isSub = null;
        if (VmSettings.USE_SUBTYPE_TABLE) {
          isSub = isSuperclassArray[((SType) expected).id];
        }

        // Remove the type check if it is the empty interface as everything subtypes it.
        if (expected instanceof InterfaceType
            && ((SType) expected).getSignatures().length == 0) {
          replace(argumentExpr);
          return argument;
        }

        // Get the primitive type check node and the type of the value
        PrimitiveTypeCheckNode node;
        SType type;
        if (argument instanceof Long) {
          node = LongTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.integerClass.type;
        } else if (argument instanceof Boolean) {
          node = BooleanTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = ((boolean) argument) ? Classes.trueClass.type : Classes.falseClass.type;
        } else if (argument instanceof Double) {
          node = DoubleTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.doubleClass.type;
        } else if (argument instanceof String) {
          node = StringTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.stringClass.type;
        } else if (argument instanceof SArray) {
          node = SArrayTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.arrayClass.type;
        } else if (argument instanceof SBlock) {
          node = SBlockTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.blockClass.type;
        } else if (argument instanceof SSymbol) {
          node = SSymbolTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.symbolClass.type;
        } else {
          /*
           * If the value is not a primitive, replace the check with the expected type stored
           * and perform the type check.
           */
          NonPrimitiveTypeCheckNode nonPrimNode =
              NonPrimitiveTypeCheckNodeFactory.create((SType) expected,
                  isSub, sourceSection, argumentExpr);
          replace(nonPrimNode);
          return nonPrimNode.check(argument, ((SObjectWithClass) argument).getSOMClass().type);
        }

        // Replace the check with primitive version and perform the type check.
        replace(node);
        node.check(argument, type);
        return argument;
      }

      // Otherwise prepare to call the type check method directly on the type
      CallTarget target = null;
      for (SInvokable invoke : expected.getSOMClass().getMethods()) {
        if (invoke.getSignature().getString().equals("matches:")) {
          target = invoke.getCallTarget();
          break;
        }
      }
      // Report an error if the method doesn't exist
      if (target == null) {
        CompilerDirectives.transferToInterpreter();
        ExceptionSignalingNode exception = insert(createExceptionNode(sourceSection));

        // TODO: Support this as yet another node
        // if (isSuper != null) {
        // isSuper.put(expected, false);
        // }
        int line = sourceSection.getStartLine();
        int column = sourceSection.getStartColumn();
        String[] parts = sourceSection.getSource().getURI().getPath().split("/");
        String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
        exception.signal(suffix + " " + expected + " is not a type");
        return null;
      }

      // Setup the cache for custom types
      EconomicMap<Object, Boolean> isSub = null;
      if (VmSettings.USE_SUBTYPE_TABLE) {
        isSub = isSuperclassTable.get(expected);
        if (isSub == null) {
          isSub = EconomicMap.create();
          isSuperclassTable.put(expected, isSub);
        }
      }

      // Replace this node with the custom type check and perform the check
      CustomTypeCheckNode node =
          CustomTypeCheckNodeFactory.create(expected, target, isSub, sourceSection,
              argumentExpr);
      replace(node);
      return node.executeEvaluated(frame, argument);
    }
  }

  /**
   * The type check node for primitives.
   */
  public abstract static class PrimitiveTypeCheckNode extends UnaryTypeCheckingNode
      implements TypeCheckingNode {

    /**
     * The expected type.
     */
    protected final SType  expected;
    /**
     * The cache of whether a type is a subtype of the expected type.
     */
    protected final byte[] isSub;

    @Child ExceptionSignalingNode exception;

    protected PrimitiveTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      this.expected = expected;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
      this.exception = createExceptionNode(sourceSection);
    }

    @Override
    public void reportError(final Object argument, final Object type,
        final Object expected, final SourceSection sourceSection,
        final ExceptionSignalingNode exception) {
      throwTypeError(argument, type, expected, sourceSection, exception);
    }

    /**
     * Check that for an argument, its type is a subtype of the expected type.
     */
    public <E> E check(final E argument, final SType type) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      // Check the cache
      if (VmSettings.USE_SUBTYPE_TABLE) {
        E result = checkTable(isSub, expected, argument, type, sourceSection, exception);
        if (result != null) {
          return result;
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      // Otherwise check if type check passes
      boolean result;
      if (argument == Nil.nilObject) {
        // Force nil object to subtype
        result = true;
      } else {
        result = expected.isSuperTypeOf(type, argument);
      }
      // Add the result to the cache
      if (isSub != null) {
        isSub[type.id] = result ? SUBTYPE : FAIL;
      }
      // Throw an error if the check didn't pass
      if (!result) {
        throwTypeError(argument, type, expected, sourceSection, exception);
      }
      // Otherwise return the argument
      return argument;
    }
  }

  /**
   * The type check node for nonprimitives. Replaces itself with a primitive specific type
   * check if a primitive is checked.
   */
  @GenerateNodeFactory
  public abstract static class NonPrimitiveTypeCheckNode extends UnaryTypeCheckingNode {

    protected final SType  expected;
    protected final byte[] isSub;

    @Child ExceptionSignalingNode exception;

    protected NonPrimitiveTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      this.expected = expected;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
    }

    @Specialization
    public boolean checkBoolean(final boolean obj) {
      check(obj, obj ? Classes.trueClass.type : Classes.falseClass.type);
      replace(BooleanTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public long checkLong(final long obj) {
      check(obj, Classes.integerClass.type);
      replace(LongTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public double checkDouble(final double obj) {
      check(obj, Classes.doubleClass.type);
      replace(DoubleTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public String checkString(final String obj) {
      check(obj, Classes.stringClass.type);
      replace(StringTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public SArray checkSArray(final SArray obj) {
      check(obj, Classes.arrayClass.type);
      replace(SArrayTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public SBlock checkSBlock(final SBlock obj) {
      check(obj, Classes.blockClass.type);
      replace(SBlockTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public SSymbol checkSSymbol(final SSymbol obj) {
      check(obj, Classes.symbolClass.type);
      replace(SSymbolTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    protected static final DispatchGuard createGuard(final Object obj) {
      return DispatchGuard.create(obj);
    }

    protected static final boolean checkGuard(final DispatchGuard guard,
        final SObjectWithClass obj) {
      try {
        return guard.entryMatches(obj, null);
      } catch (InvalidAssumptionException e) {
      } catch (IllegalArgumentException e) {
      }
      return false;
    }

    @Specialization(guards = "checkGuard(guard, obj)")
    public SObjectWithClass checkSObject(final SObjectWithClass obj,
        @Cached("createGuard(obj)") final DispatchGuard guard,
        @Cached("check(obj, obj.getSOMClass().type)") final Object initialRcvrUnused) {
      return obj;
    }

    // FIXME: The above should be fine but causes valid objects to not match a specialisation
    // without the following -egt
    @Specialization
    public SObjectWithClass checkSObject(final SObjectWithClass obj) {
      return check(obj, obj.getSOMClass().type);
    }

    /**
     * Check that for an argument, its type is a subtype of the expected type.
     */
    public <E> E check(final E argument, final SType type) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      // Check the cache
      if (VmSettings.USE_SUBTYPE_TABLE) {
        byte sub = isSub[type.id];
        if (sub == SUBTYPE) {
          return argument;
        } else if (sub == FAIL) {
          throwTypeError(argument, type, expected, sourceSection, exception);
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      // Otherwise check if type check passes
      boolean result;
      if (argument == Nil.nilObject) {
        // Force nil object to subtype
        result = true;
      } else {
        result = expected.isSuperTypeOf(type, argument);
      }
      // Add the result to the cache
      if (isSub != null) {
        isSub[type.id] = result ? SUBTYPE : FAIL;
      }
      // Throw an error if the check didn't pass
      if (!result) {
        throwTypeError(argument, type, expected, sourceSection, exception);
      }
      // Otherwise return the argument
      return argument;
    }
  }

  /**
   * Type check node for a type known to subtype longs.
   */
  @GenerateNodeFactory
  public abstract static class LongTypeCheckNode extends PrimitiveTypeCheckNode {
    protected LongTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public long typeCheckLong(final long argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for a type known to subtype booleans.
   */
  @GenerateNodeFactory
  public abstract static class BooleanTypeCheckNode extends PrimitiveTypeCheckNode {
    protected BooleanTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public boolean typeCheckBoolean(final boolean argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for a type known to subtype doubles.
   */
  @GenerateNodeFactory
  public abstract static class DoubleTypeCheckNode extends PrimitiveTypeCheckNode {
    protected DoubleTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public double typeCheckDouble(final double argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for a type known to subtype strings.
   */
  @GenerateNodeFactory
  public abstract static class StringTypeCheckNode extends PrimitiveTypeCheckNode {
    protected StringTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public String typeCheckString(final String argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for a type known to subtype arrays.
   */
  @GenerateNodeFactory
  public abstract static class SArrayTypeCheckNode extends PrimitiveTypeCheckNode {
    protected SArrayTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public SArray typeCheckArray(final SArray argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for a type known to subtype blocks.
   */
  @GenerateNodeFactory
  public abstract static class SBlockTypeCheckNode extends PrimitiveTypeCheckNode {
    protected SBlockTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public SBlock typeCheckBlock(final SBlock argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for a type known to subtype symbols.
   */
  @GenerateNodeFactory
  public abstract static class SSymbolTypeCheckNode extends PrimitiveTypeCheckNode {
    protected SSymbolTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public SSymbol typeCheckSymbol(final SSymbol argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckOther(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  /**
   * Type check node for brands.
   */
  @GenerateNodeFactory
  public abstract static class BrandTypeCheckNode extends UnaryTypeCheckingNode
      implements TypeCheckingNode {
    protected final BrandType brand;

    protected BrandTypeCheckNode(final BrandType type, final SourceSection sourceSection) {
      this.brand = type;
      this.sourceSection = sourceSection;
      this.exception = createExceptionNode(sourceSection);
    }

    @Child ExceptionSignalingNode exception;

    @Override
    public void reportError(final Object argument, final Object type, final Object expected,
        final SourceSection sourceSection, final ExceptionSignalingNode exception) {
      throwTypeError(argument, type, expected, sourceSection, exception);
    }

    @Specialization
    public Object executeEvaluated(final Object argument) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }
      // Don't use cache as brands use object identity and not the type
      if (brand.isSuperTypeOf(null, argument)) {
        return argument;
      }
      // TODO: Optimize
      SType argType = Types.getClassOf(argument).type;
      throwTypeError(argument, argType, brand, sourceSection, exception);
      return null;
    }
  }

  /**
   * Type check node for user-defined types.
   */
  @GenerateNodeFactory
  public abstract static class CustomTypeCheckNode extends UnaryTypeCheckingNode
      implements TypeCheckingNode {

    /**
     * The expectd "type".
     */
    protected final SObjectWithClass             expected;
    /**
     * The call target to invoke the type check.
     */
    protected final CallTarget                   target;
    /**
     * The subtype cache. Assumes that the expected type is implemented to be consistant for
     * all objects of the same type.
     */
    protected final EconomicMap<Object, Boolean> isSub;

    @Child ExceptionSignalingNode exception;

    protected CustomTypeCheckNode(final SObjectWithClass expected, final CallTarget target,
        final Object isSub_TRUFFLE_REGRESSION, final SourceSection sourceSection) {
      // Truffle is currently failing to generate correct code for this
      @SuppressWarnings("unchecked")
      EconomicMap<Object, Boolean> isSub =
          (EconomicMap<Object, Boolean>) isSub_TRUFFLE_REGRESSION;
      this.expected = expected;
      this.target = target;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
      this.exception = createExceptionNode(sourceSection);
    }

    @Override
    public void reportError(final Object argument, final Object type, final Object expected,
        final SourceSection sourceSection, final ExceptionSignalingNode exception) {
      throwTypeError(argument, type, expected, sourceSection, exception);
    }

    @Specialization
    public Object executeEvaluated(final Object argument) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      // Check the cache
      if (VmSettings.USE_SUBTYPE_TABLE) {
        Object result = null;
        SType argType = Types.getClassOf(argument).type;
        if (isSub.containsKey(argType)) {
          if (isSub.get(argType)) {
            result = argument;
          } else {
            throwTypeError(argument, argType, expected, sourceSection, exception);
          }
        }
        if (result != null) {
          return result;
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      // Otherwise execute the type check method on the expected type
      CompilerDirectives.transferToInterpreterAndInvalidate();
      boolean result = (Boolean) Truffle.getRuntime().createDirectCallNode(target)
                                        .call(new Object[] {expected, argument});
      // Since it finished executing, the type check passed
      SType argType = Types.getClassOf(argument).type;
      if (isSub != null) {
        isSub.put(argType, result);
      }
      if (!result) {
        throwTypeError(argument, argType, expected, sourceSection, exception);
      }
      return argument;
    }
  }
}
