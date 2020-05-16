//def doneBrand: Unknown = brand
type Done = interface {} //doneBrand.Type
//doneBrand.brand(done)

class brand -> Unknown {
  def Type: Unknown = VmMirror.typeNewBrand(done)

  method brand(obj: Unknown) -> Done {
     VmMirror.brand(obj)with(Type)
  }
}

type iso = VmMirror.typeCapability("ISOLATE")
type imm = VmMirror.typeCapability("IMMUTABLE")
type local = VmMirror.typeCapability("LOCAL")
type unsafe = VmMirror.typeCapability("UNSAFE")

class newChannel {
  def inner = platform.processes.Channel.new

  method imm { ioImm(inner.in, inner.out) }
  method iso { ioIso(inner.in, inner.out) }
  method local { ioLocal(inner.in, inner.out) }
  method unsafe { ioUnsafe(inner.in, inner.out) }

  //Unchecked
  method read { inner.in.read }
  method <- (value) { inner.out.write(value) }
}

class ioImm(in, out) {
  method read { imm.cast(in.read) }
  method <- (value) { out.write(imm.cast(value)) }
}
class ioIso(in, out) {
  method read { iso.cast(in.read) }
  method <- (value) { out.write(iso.cast(value)) }
}
class ioLocal(in, out) {
  method read { local.cast(in.read) }
  method <- (value) { out.write(local.cast(value)) }
}
class ioUnsafe(in, out) {
  method read { unsafe.cast(in.read) }
  method <- (value) { out.write(unsafe.cast(value)) }
}

def null = done

method let(x) in(b) {
    b.apply(x)// := null)
}

//class newChannel {
//  def immInner = platform.processes.Channel.new
//  def isoInner = platform.processes.Channel.new
//  def localInner = platform.processes.Channel.new
//  def unsafeInner = platform.processes.Channel.new
//
//  method imm { ioImm(immInner) }
//  method iso { ioIso(isoInner) }
//  method local { ioLocal(localInner) }
//  method unsafe { ioUnsafe(unsafeInner) }
//}
//
//class ioImm(io) {
//  method read { imm.cast(io.in.read) }
//  method <- (value) { io.out.write(imm.cast(value)) }
//}
//class ioIso(io) {
//  method read { iso.cast(io.in.read) }
//  method <- (value) { io.out.write(iso.cast(value)) }
//}
//class ioLocal(io) {
//  method read { local.cast(io.in.read) }
//  method <- (value) { io.out.write(local.cast(value)) }
//}
//class ioUnsafe(io) {
//  method read { unsafe.cast(io.in.read) }
//  method <- (value) { io.out.write(unsafe.cast(value)) }
//}

method spawn (b) {
    var channel := newChannel
    platform.threading.Task.spawn (b) with [channel]
    channel
}

type Number = interface {
  + (other)
  - (other)
  / (other)
  * (other)
  asString
}

type String = interface {
  ++ (other)
}

type Boolean = interface {
  and (other)
  or (other)
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

method for (r: List) do (body: Invokable) -> Done {
  r.do(body)
  done
}

method valueOf (exp: Invokable) -> Unknown {
  exp.apply
}