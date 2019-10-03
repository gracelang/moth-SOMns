method asString {"types.grace"}

type Foo = {
  x
  y
}

def aFoo = object {
  var x
  var y
}

method withNumberArg(x: Number) {}
method withStringArg(x: String) {}
method withBooleanArg(x: Boolean) {}
method withFooArg(x: Foo) {}

method testTypedArgPasses {
  withNumberArg(1)
  withStringArg("hello")
  withBooleanArg(true)
  withFooArg(aFoo)
  "testTypedArgPasses passed"
}

method testTypedArgFailures {
  { 
    withNumberArg(true);
    error("testTypedArgFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withNumberArg("hello");
    error("testTypedArgFailures failed, didn't produce error for String (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withNumberArg(aFoo);
    error("testTypedArgFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withStringArg(true);
    error("testTypedArgFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withStringArg(1);
    error("testTypedArgFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withStringArg(aFoo);
    error("testTypedArgFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withBooleanArg(1);
    error("testTypedArgFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withBooleanArg("hello");
    error("testTypedArgFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withBooleanArg(aFoo);
    error("testTypedArgFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withFooArg(1);
    error("testTypedArgFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withFooArg("hello");
    error("testTypedArgFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withFooArg(true);
    error("testTypedArgFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  "testTypedArgFailures passed"
}

method returnArgAsNumber(x) -> Number { x }
method returnArgAsString(x) -> String { x }
method returnArgAsBoolean(x) -> Boolean { x }
method returnArgAsFoo(x) -> Foo { x }

method testTypedReturnPasses {
  returnArgAsNumber(1)
  returnArgAsString("hello")
  returnArgAsBoolean(true)
  returnArgAsFoo(aFoo)
  "testTypedReturnPasses passed"
}

method testTypedReturnFailures {
  { 
    returnArgAsNumber(true);
    error("testTypedReturnFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsNumber("hello");
    error("testTypedReturnFailures failed, didn't produce error for String (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsNumber(aFoo);
    error("testTypedReturnFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsString(true);
    error("testTypedReturnFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsString(1);
    error("testTypedReturnFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsString(aFoo);
    error("testTypedReturnFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsBoolean(1);
    error("testTypedReturnFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsBoolean("hello");
    error("testTypedReturnFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsBoolean(aFoo);
    error("testTypedReturnFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsFoo(1);
    error("testTypedReturnFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsFoo("hello");
    error("testTypedReturnFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsFoo(true);
    error("testTypedReturnFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  "testTypedReturnFailures passed"
}

method testTypedLocalAssignmentPasses {
  var aNumberField: Number := 1
  var aStringField: String := "hello"
  var aBooleanField: Boolean := true
  var aFooField: Foo := aFoo
  "testTypedLocalAssignmentPasses passed"
}

method testTypedLocalAssignmentFailures {
  {
    var x: Number := "hello"
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Number := true
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Number := aFoo
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: String := 1
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: String := true
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: String := aFoo
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Boolean := 1
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Boolean := "hello"
    error("testTypedLocalAssignmentFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Boolean := aFoo
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}
  
  {
    var x: Foo := 1
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Foo := "hello"
    error("testTypedLocalAssignmentFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Foo := true
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  "testTypedLocalAssignmentFailures passed"
}

method testTypedFieldAssignmentPasses {
  object {
    var aNumberField: Number := 1
    var aStringField: String := "hello"
    var aBooleanField: Boolean := true
    var aFooField: Foo := aFoo
  }

  "testTypedFieldAssignmentPasses passed"
}

method testTypedFieldAssignmentFailures {
  {
    object {
      var x: Number := "hello"
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for String (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Number := true
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Number := aFoo
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: String := 1
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: String := true
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: String := aFoo
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Boolean := 1
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Boolean := "hello"
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Boolean := aFoo
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}
  
  {
    object {
      var x: Foo := 1
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Foo := "hello"
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Foo := true
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  "testTypedFieldAssignmentFailures passed"
}

method testExplicitCheckPasses {
  Number.checkOrError(5)
  String.checkOrError("5")
  (interface {}).checkOrError(5)

  "testExplicitCheckPasses passed"
} 

method testExplicitCheckFailures {
  {
    Number.checkOrError("5")
    error("testExplicitCheckFailures failed, didn't produce error when checking if a string was a number")
  }.on (platform.kernel.TypeError) do {}

  {
    String.checkOrError(5)
    error("testExplicitCheckFailures failed, didn't produce error when checking if a number was a string")
  }.on (platform.kernel.TypeError) do {}

  "testExplicitCheckFailures passed"
}

method testBrandPasses {
  def myBrand = brand
  myBrand.brand(5)
  myBrand.brand("test")

  def a : myBrand.Type = 5
  def b : myBrand.Type = "test"
  "testBrandPasses passed"
} 

method testBrandChanges {
  def myBrand = brand

  {
    def a : myBrand.Type = 5
    error("testBrandChanges failed, didn't produce error for a value not in the brand")
  }.on (platform.kernel.TypeError) do {}
  
  myBrand.brand(5)

  def b : myBrand.Type = 5
  "testBrandChanges passed"
} 

method testBrandFailures {
  {
    def x: brand.Type = "hello"
    error("testBrandFailures failed, didn't produce error for a value not in a fresh brand")
  }.on (platform.kernel.TypeError) do {}
  "testBrandFailures passed"
}

method testTypeUnionPasses {
  def num : Number + String  = "5"
  "testTypeUnionPasses passed"
} 

def x : TestType = 1
class TestType -> Unknown {
      method checkOrError(a: Unknown) -> Unknown {
        if (a == 1) then { 
          return a
        } else {
          platform.kernel.TypeError.signal("Not 1")
        }
      }
    }

method testCustomType {
  //Note the type breaks the assumption for how types are consistent among objects of the same type.

  def x : TestType = 1

  {
    def y : TestType = "test"
    error("testCustomType failed, didn't produce error as intended")
  }.on (platform.kernel.TypeError) do {}
  "testCustomType passed"
}
