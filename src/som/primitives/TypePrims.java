package som.primitives;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.Types;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SType;
import som.vmobjects.SType.BrandType;


public final class TypePrims {

  /**
   * VM Hook to set the class for types.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "typeClass:")
  public abstract static class SetTypeClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SType.setSOMClass(value);
      return value;
    }
  }

  /**
   * Type intersection. Symbol selector is found in BitAndPrim.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "type:intersect:")
  @Primitive(selector = "intersect:", receiverType = SType.class)
  public abstract static class TypeIntersectPrim extends BinaryExpressionNode {
    // TODO: Add specialization for custom types (so don't only expect SType)

    @Specialization
    public Object performTypeCheckOnNil(final SType left, final SType right) {
      return new SType.IntersectionType(left, right);
    }

  }

  /**
   * VM Hook to create the type used by brands.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "typeNewBrand:")
  public abstract static class CreateTypeBrandPrim extends UnaryExpressionNode {

    @Specialization
    public Object createBrand(final Object o) {
      return new SType.BrandType();
    }
  }

  /**
   * VM Hook for the brand type to record the object as being branded.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "brand:with:")
  public abstract static class BrandObjectWithBrandPrim extends BinaryExpressionNode {

    @Specialization
    public Object brandObject(final Object o, final BrandType brand) {
      brand.brand(o);
      return Nil.nilObject;
    }

  }

  /**
   * Type union creating a variant type.
   */
  @GenerateNodeFactory
  @Primitive(selector = "|", receiverType = SType.class)
  public abstract static class TypeVariantPrim extends BinaryExpressionNode {

    @Specialization
    public final Object doTypeVariant(final SType left, final SType right) {
      return new SType.VariantType(left, right);
    }

  }

  /**
   * VM Hook used as default behaviour when directly calling the type check method on a type.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "type:matches:")
  public abstract static class TypeCheckPrim extends BinaryExpressionNode {

    protected void throwTypeError(final SType expected, final Object obj) {
      CompilerDirectives.transferToInterpreter();

      ExceptionSignalingNode exception = insert(
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection));

      Object type = Types.getClassOf(obj).type;
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      // TODO: Get the real source of the type check
      // String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      // String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
      String suffix = "{UNKNOWN-FILE} [" + line + "," + column + "]";
      exception.signal(
          suffix + " \"" + obj + "\" is not a subtype of " + expected
              + ", because it has the type " + type);
    }

    protected SClass getClass(final Object obj) {
      return Types.getClassOf(obj);
    }

    protected boolean isNil(final SObjectWithoutFields obj) {
      return obj == Nil.nilObject;
    }

    @Specialization(guards = {"isNil(obj)"})
    public Object performTypeCheckOnNil(final SType expected, final SObjectWithoutFields obj) {
      return true;
    }

    @Specialization
    public Object checkObject(final SType expected, final SObjectWithClass obj) {
      return expected.isSuperTypeOf(obj.getSOMClass().type, obj);
    }

    @Specialization
    public Object checkBoolean(final SType expected, final boolean obj) {
      return expected.isSuperTypeOf(obj ? Classes.trueClass.type : Classes.falseClass.type,
          obj);
    }

    @Specialization
    public Object checkLong(final SType expected, final long obj) {
      return expected.isSuperTypeOf(Classes.integerClass.type, obj);
    }

    @Specialization
    public Object checkDouble(final SType expected, final double obj) {
      return expected.isSuperTypeOf(Classes.doubleClass.type, obj);
    }

    @Specialization
    public Object checkString(final SType expected, final String obj) {
      return expected.isSuperTypeOf(Classes.stringClass.type, obj);
    }

    @Specialization
    public Object checkSArray(final SType expected, final SArray obj) {
      return expected.isSuperTypeOf(Classes.arrayClass.type, obj);
    }

    @Specialization
    public Object checkSBlock(final SType expected, final SBlock obj) {
      return expected.isSuperTypeOf(Classes.blockClass.type, obj);
    }

    @Specialization
    public Object executeEvaluated(final Object expected, final Object argument) {
      ExceptionSignalingNode exception = insert(
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection));
      exception.signal("The type has not defined the meaning of what it is to be a type.");
      return false;
    }
  }
}
