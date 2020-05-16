package som.interpreter.actors;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.vm.NotYetImplementedException;
import som.vm.constants.Nil;
import som.vmobjects.Capability;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public final class CopyObjects {

  public static final Object copyNested(final Object o, final Capability capability,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    VM.thisMethodNeedsToBeOptimized("This should probably be optimized");

    // Corresponds to TransferObject.isTransferObject()
    if ((o instanceof SObject && ((SObject) o).getSOMClass().isTransferObject())) {
      return CopyObjects.transfer((SObject) o, capability, transferedObjects);
    } else if (o instanceof STransferArray) {
      return CopyObjects.transfer((STransferArray) o, capability, transferedObjects);
    } else if (o instanceof SObjectWithoutFields
        && ((SObjectWithoutFields) o).getSOMClass().isTransferObject()) {
      return CopyObjects.transfer((SObjectWithoutFields) o, capability, transferedObjects);
    }
    return o;
  }

  @TruffleBoundary
  public static SObjectWithoutFields transfer(final SObjectWithoutFields obj,
      final Capability capability,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    SObjectWithoutFields newObj = obj.cloneBasics();
    if (transferedObjects != null) {
      transferedObjects.put(obj, newObj);
    }
    newObj.capability = capability;
    return newObj;
  }

  @TruffleBoundary
  public static SObject transfer(final SObject obj, final Capability capability,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {

    ObjectLayout layout = obj.getObjectLayout();
    EconomicMap<SlotDefinition, StorageLocation> fields = layout.getStorageLocations();
    SObject newObj = obj.cloneBasics();

    Map<SAbstractObject, SAbstractObject> transferMap =
        takeOrCreateTransferMap(transferedObjects);

    assert !transferMap.containsKey(
        obj) : "The algorithm should not transfer an object twice.";
    transferMap.put(obj, newObj);

    for (StorageLocation location : fields.getValues()) {
      if (location.isObjectLocation()) {
        Object orgObj = location.read(obj);

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = copyNested(orgObj, capability, transferMap);
        }
        location.write(newObj, trnfObj);
      }
    }
    newObj.capability = capability;
    return newObj;
  }

  @TruffleBoundary
  public static STransferArray transfer(final STransferArray arr,
      final Capability capability,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    STransferArray newObj = arr.cloneBasics();

    if (newObj.isSomePrimitiveType() || newObj.isEmptyType()) {
      return newObj; // we are done in this case
    }

    assert newObj.isPartiallyEmptyType() || newObj.isObjectType();

    Map<SAbstractObject, SAbstractObject> transferMap =
        takeOrCreateTransferMap(transferedObjects);

    assert !transferMap.containsKey(
        arr) : "The algorithm should not transfer an object twice.";
    transferMap.put(arr, newObj);

    if (newObj.isObjectType()) {
      Object[] storage = newObj.getObjectStorage();

      for (int i = 0; i < storage.length; i++) {
        Object orgObj = storage[i];

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = copyNested(orgObj, capability, transferMap);
        }

        storage[i] = trnfObj;
      }
    } else if (newObj.isPartiallyEmptyType()) {
      PartiallyEmptyArray parr =
          newObj.getPartiallyEmptyStorage();
      Object[] storage = parr.getStorage();

      for (int i = 0; i < storage.length; i++) {
        Object orgObj = storage[i];

        if (orgObj == Nil.nilObject) {
          continue;
        }

        // if it was already transfered, take it from the map, otherwise, handle it
        Object trnfObj = transferMap.get(orgObj);
        if (trnfObj == null) {
          trnfObj = copyNested(orgObj, capability, transferMap);
        }

        storage[i] = trnfObj;
      }
    } else {
      CompilerDirectives.transferToInterpreter();
      assert false : "Missing support for some storage type";
      throw new NotYetImplementedException();
    }
    newObj.capability = capability;
    return newObj;
  }

  protected static Map<SAbstractObject, SAbstractObject> takeOrCreateTransferMap(
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    Map<SAbstractObject, SAbstractObject> transferMap;
    if (transferedObjects != null) {
      transferMap = transferedObjects;
    } else {
      transferMap = new HashMap<SAbstractObject, SAbstractObject>();
    }
    return transferMap;
  }
}
