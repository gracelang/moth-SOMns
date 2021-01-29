//def doneBrand: Unknown = brand
type Done = interface {} //doneBrand.Type 
//doneBrand.brand(done)

class brand -> Unknown {
  def Type: Unknown = VmMirror.typeNewBrand(done)

  method brand(obj: Unknown) -> Done {
     VmMirror.brand(obj)with(Type)
  }
}

type Number = interface {
  + (other)
  - (other)
  / (other)
  * (other)
  asString
  matches (obj)
}

type String = interface {
  ++ (other)
  matches (obj)
}

type Boolean = interface {
  and (other)
  or (other)
  matches (obj)
}

type List = interface {
  at(ix)
  at(ix)put(value)
  size
}

type Invokable = interface {
  apply
}

method print(x: Unknown) -> Done {
  x.println
}

method if (cond: Boolean) then (blk: Invokable) -> Unknown {
  cond.ifTrue(blk)
}

method if (cond: Boolean) then (ib: Invokable) else (eb: Invokable) -> Unknown {
  cond.ifTrue(ib) ifFalse(eb)
}

method repeat (n: Number) times (blk: Invokable) -> Done {
  n.round.timesRepeat(blk) // Should be ceil
  done
}

method while (cond: Invokable) do (body: Invokable) -> Done {
  cond.apply.ifTrue {
     body.apply
     return while (cond) do (body)
  }
  done
}

method do (body: Invokable) while (cond: Invokable) -> Done {
  body.apply
  cond.apply.ifTrue {
     return while (cond) do (body)
  }
  done
}

method valueOf (exp: Invokable) -> Unknown {
  exp.apply
}

method match (obj: Unknown) case (case1: Invokable) else (elseBody: Invokable) -> Unknown {
    if(case1.matches(obj)) then {
      case1.apply
    } else {
      elseBody.apply
    }
}

method match (obj: Unknown) case (case1: Invokable) case (case2: Invokable) else (elseBody: Invokable) -> Unknown {
    if(case1.matches(obj)) then {
      case1.apply
    } else {
      if (case2.matches(obj)) then {
        case2.apply
      } else {
        elseBody.apply
      }
    }
}