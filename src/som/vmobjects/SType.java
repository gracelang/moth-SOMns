package som.vmobjects;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.interpreter.nodes.TypeCheckNode;
import som.vm.VmSettings;


public abstract class SType extends SObjectWithClass {
  /**
   * The class for types.
   */
  @CompilationFinal private static SClass typeClass;
  /**
   * Since types can be created before the class object for types is created, this is the list
   * of types that are missing their class. Is null once type class has been set.
   */
  private static List<SType>              missingClass = new LinkedList<>();
  /**
   * The current number of type objects created. Used to generate IDs.
   */
  private static int                      count        = 0;

  /**
   * A unique identifier for the type.
   */
  public final int id;

  /**
   * Set the class object for types and add the class to previously created types that are
   * missing the class.
   *
   * @param cls - the type class object
   */
  public static void setSOMClass(final SClass cls) {
    typeClass = cls;
    // Add the missing class
    for (SType typeWithoutClass : missingClass) {
      typeWithoutClass.setClass(typeClass);
    }
    // Remove the list
    missingClass = null;
  }

  public SType() {
    super();
    this.capability = Capability.IMMUTABLE;
    // Either add the type class now or record this type to do so later
    if (typeClass == null) {
      missingClass.add(this);
    } else {
      this.setClass(typeClass);
    }

    if (VmSettings.COLLECT_TYPE_STATS) {
      ++TypeCheckNode.nTypes;
    }

    id = count++;
  }

  @Override
  public boolean isValue() {
    return true;
  }

  /**
   * Gets the interface of the type.
   *
   * @return The symbols representing the method names belong to the interface. If the type
   *         does not have a specific interface, then the array is empty.
   */
  public abstract SSymbol[] getSignatures();

  /**
   * Checks whether this type is a super type of the given type and object.
   *
   * @param other - the type that is potentially a sub type
   * @param inst - the object of being check as a sub type
   * @return Whether this type is a super type.
   */
  public abstract boolean isSuperTypeOf(SType other, Object inst);

  /**
   * Represents a type describing the public methods of an object.
   */
  public static class InterfaceType extends SType {
    @CompilationFinal(dimensions = 1) private final SSymbol[] signatures;

    public InterfaceType(final SSymbol[] signatures) {
      this.signatures = signatures;
    }

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      for (SSymbol sigThis : signatures) {
        // Find the signature in the other type
        boolean found = false;
        for (SSymbol sigOther : other.getSignatures()) {
          if (sigThis == sigOther) {
            found = true;
            break;
          }
        }
        // Otherwise this cannot be a super type
        if (!found) {
          return false;
        }
      }
      // All signatures found so must be a super type
      return true;
    }

    @Override
    public SSymbol[] getSignatures() {
      return signatures;
    }

    @Override
    public String toString() {
      String s = "interface {";
      boolean first = true;
      for (SSymbol sig : signatures) {
        // Methods starting with "!!!" are masked writes and a public version of the write
        // should already be one of the other signatures
        if (sig.getString().startsWith("!!!")) {
          continue;
        }

        if (first) {
          s += "'" + sig.getString() + "'";
          first = false;
        } else {
          s += ", '" + sig.getString() + "'";
        }
      }
      return s + "}";
    }
  }

  // SELF TYPE???

  /**
   * Represents a type that is a super type if both left and righthand branches are super
   * types. For interface types, it acts as the union of signatures.
   */
  public static class IntersectionType extends SType {

    public final SType left;
    public final SType right;

    public IntersectionType(final SType left, final SType right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      return left.isSuperTypeOf(other, inst) && right.isSuperTypeOf(other, inst);
    }

    @Override
    public SSymbol[] getSignatures() {
      // The
      Set<SSymbol> set = new HashSet<>();
      set.addAll(Arrays.asList(left.getSignatures()));
      set.addAll(Arrays.asList(right.getSignatures()));
      return set.toArray(new SSymbol[set.size()]);
    }
  }

  /**
   * Represents a type that is a super type if either of the left and righthand branches are
   * super types. Note this does NOT act as the intersection of signatures for interface types.
   */
  public static class VariantType extends SType {

    public final SType left;
    public final SType right;

    public VariantType(final SType left, final SType right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      return left.isSuperTypeOf(other, inst) || right.isSuperTypeOf(other, inst);
    }

    @Override
    public SSymbol[] getSignatures() {
      // Signatures from variant types are not usable
      return new SSymbol[] {};
    }
  }

  /**
   * Represents a type that is a super type if an object has been branded by the corresponding
   * brand object.
   */
  public static class BrandType extends SType {

    private final EconomicSet<Object> brandedObjects = EconomicSet.create();

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      return brandedObjects.contains(inst);
    }

    @Override
    public SSymbol[] getSignatures() {
      // Signatures from brand types are not usable
      return new SSymbol[] {};
    }

    public void brand(final Object o) {
      brandedObjects.add(o);
    }

    @Override
    public String toString() {
      return "a Brand";
    }
  }

  /**
   * Represents a type that contains all objects of a certain capability.
   */
  public static class CapabilityType extends SType {

    private final Capability capability;

    public CapabilityType(final Capability capability) {
      this.capability = capability;
    }

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      if (inst instanceof SAbstractObject) {
        return capability.equals(((SAbstractObject) inst).capability);
      }
      // All other values must be immutable
      return capability == Capability.IMMUTABLE;
    }

    @Override
    public SSymbol[] getSignatures() {
      // Signatures from brand types are not usable
      return new SSymbol[] {};
    }

    @Override
    public String toString() {
      return capability.toString();
    }
  }
}
