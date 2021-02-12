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

type Pattern = interface {
    matches(other)
}

type ExceptionPacket = interface {
    exception
    data
    lineNumber
    moduleNumber
    backtrace
}

class exceptionKind -> pattern {
    var parent := self
    var name := "exceptionKind"    

    method refine(nam) {
        var e := exceptionKind
        e.parent := self
        e.name := nam
        e
    }
    
    method raise(message) {
        var excepPack := exceptionPacket(self, message, "", 0, 0, 0)
        kernel.GraceException.raiseWith(excepPack)
    }
    
    method raise(message) with(data) {
        var excepPack := exceptionPacket(self, message, data, 0, 0, 0)
        kernel.GraceException.raiseWith(excepPack)
    }
    
    method equals (other) { // FIXME: change to == either in java 
        (parent.name == other.parent.name) && (name == other.name) // FIXME: parent.equals(other.parent) doesn't work
    }

    method matches (other) {
        self.equals(other)
    }
}

class exceptionPacket(e, msg, excepData, line, module, back) -> Unknown { // TODO: set up vm hooks to get line, module and backtrace
    var exception := e
    var message := msg
    var data := excepData
    var lineNumber := line
    var moduleName := module
    var backtrace := back
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

def foreignException = exceptionKind.refine("foreignException")

method try (body: Invokable) catch (catchBody1: Invokable) -> Done {
    {
        body.on(kernel.GraceException) do { err ->
            if(catchBody1.matches(err.packet.exception)) then { 
                catchBody1.apply(err.packet)
                exit
            } else {
                err.signal
            }
        }
    }.on(kernel.Exception) do { err ->
        if(catchBody1.matches(foreignException)) then {
            var errPacket := exceptionPacket("", err, foreignException)
            catchBody1.apply(errPacket)
            exit
        } else {
            err.signal
        }
    }
    
}

method try (body: Invokable) finally (final: Invokable) -> Done {
    body.apply
    final.apply
}

method try (body: Invokable) catch (catchBody1: Invokable) finally (final) -> Done {
    {
        body.on(kernel.GraceException) do { err ->
            if(catchBody1.matches(err.packet.exception)) then { 
                catchBody1.apply(err.packet)
                final.apply
                exit
            } else {
                err.signal
            }
        }
    }.on(kernel.Exception) do { err ->
        if(catchBody1.matches(foreignException)) then {
            var errPacket := exceptionPacket("", err, foreignException)
            catchBody1.apply(errPacket)
            final.apply
            exit
        } else {
            err.signal
        }
    }
    final.apply
}

method try (body: Invokable) catch (catchBody1: Invokable) catch (catchBody2: Invokable) -> Done {
    {
        body.on(kernel.GraceException) do { err ->
            if(catchBody1.matches(err.packet.exception)) then { 
                catchBody1.apply(err.packet)
                exit
            } else {
                if(catchBody2.matches(err.packet.exception)) then { 
                    catchBody2.apply(err.packet)
                    exit
                } else {
                    err.signal
                }
            }
        }
    }.on(kernel.Exception) do { err ->
        if(catchBody1.matches(foreignException)) then {
            var errPacket := exceptionPacket("", err, foreignException)
            catchBody1.apply(errPacket)
            exit
        } else {
            if(catchBody2.matches(foreignException)) then {
                var errPacket := exceptionPacket("", err, foreignException)
                catchBody1.apply(errPacket)
                exit
            } else {
                err.signal
            }
        }
    }
}

method try (body: Invokable) catch (catchBody1: Invokable) catch (catchBody2: Invokable) finally (final) -> Done {
    {
        body.on(kernel.GraceException) do { err ->
            if(catchBody1.matches(err.packet.exception)) then { 
                catchBody1.apply(err.packet)
                final.apply
                exit
            } else {
                if(catchBody2.matches(err.packet.exception)) then { 
                    catchBody2.apply(err.packet)
                    final.apply
                    exit
                } else {
                    err.signal
                }
            }
        }
    }.on(kernel.Exception) do { err ->
        if(catchBody1.matches(foreignException)) then {
            var errPacket := exceptionPacket("", err, foreignException)
            catchBody1.apply(errPacket)
            final.apply
            exit
        } else {
            if(catchBody2.matches(foreignException)) then {
                var errPacket := exceptionPacket("", err, foreignException)
                catchBody1.apply(errPacket)
                final.apply
                exit
            } else {
                err.signal
            }
        }
    }
    final.apply
}

// TODO: allow matching for booleans. Return successfullMatch or failedMatch object instead of true or false.
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
        if(case2.matches(obj)) then {
            case2.apply
        } else {
            elseBody.apply
        }
    }
}

method match (obj: Unknown) case (case1: Invokable) case (case2: Invokable) case (case3: Invokable) else (elseBody: Invokable) -> Unknown {
    if(case1.matches(obj)) then {
      case1.apply
    } else {
        if(case2.matches(obj)) then {
            case2.apply
        } else {
            if(case3.matches(obj)) then {
                case3.apply
            } else {
                elseBody.apply
            }
        }
    }
}

method match (obj: Unknown) case (case1: Invokable) case (case2: Invokable) case (case3: Invokable) case (case4: Invokable) else (elseBody: Invokable) -> Unknown {
    if(case1.matches(obj)) then {
      case1.apply
    } else {
        if(case2.matches(obj)) then {
            case2.apply
        } else {
            if(case3.matches(obj)) then {
                case3.apply
            } else {
                if(case4.matches(obj)) then {
                    case4.apply
                } else {
                    elseBody.apply
                }
            }
        }
    }
}

method match (obj: Unknown) case (case1: Invokable) case (case2: Invokable) case (case3: Invokable) case (case4: Invokable) case (case5: Invokable) else (elseBody: Invokable) -> Unknown {
    if(case1.matches(obj)) then {
      case1.apply
    } else {
        if(case2.matches(obj)) then {
            case2.apply
        } else {
            if(case3.matches(obj)) then {
                case3.apply
            } else {
                if(case4.matches(obj)) then {
                    case4.apply
                } else {
                    if(case5.matches(obj)) then {
                        case5.apply
                    } else {
                        elseBody.apply
                    }
                }
            }
        }
    }
}
