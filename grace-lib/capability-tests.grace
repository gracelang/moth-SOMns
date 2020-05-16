class isoTest(n) {
    use isolate
    var x is public
    var count := 0
    def asString is public = "<iso {n}>"
    method f(z) {
        count := count + 1
        return count
    }
}

class localTest(n) {
    use local
    var x is public
    var count := 0
    def asString is public = "<local {n}>"
    method f(z) {
        count := count + 1
        return count
    }
}

print("\n\nTest 1")
var x1 := isoTest 1
print(x1)

print("\n\nTest 2")
var x2 := localTest 2
print(x2)

print("\n\nTest 3")
var x3 := isoTest 3
x3.x := isoTest 3.1
print(x3.x)
x3.x := object { use isolate }
print(x3.x)
//x1.x := x3.x //fails due to already aliased not part of Michael's code
print(x3)

print("\n\nTest 4")
var x4 := localTest 4
x4.x := isoTest 4.1
x4.x := localTest 4.2

print("\n\nTest 5")
var x5 := isoTest 5
x5.x := 5.1

print("\n\nTest 6")
var x6 := localTest 6
var y6 := x6

//These should break
print("\n\nTest 7")
var x := isoTest 7
//x.x := object {}

print("\n\nTest 8")
var x8 := isoTest 8
//x8.x := object { use local }

print("\n\nTest 9")
var x9 := isoTest 9
//var y9 := x9

//Check local variable uses proper error instead of just runtime
method test9 () {
    var x9y := isoTest 9
    var y9y := x9y
}
//test()

print("\n\nTest 10")
var x10 := localTest 10
//x10.x := object {}

//These should work
print("\n\nTest 11")
var x11 := isoTest 11
var y11 := (x11 := null)
print(y11)

print("\n\nTest 12")
var x12 := isoTest 12
x12.x := isoTest 12.1
let (x12.x := null) in { v -> v.asString }

print("\n\nTest 13")
var x13 := isoTest 13
x13.x := isoTest 13.1
method tap(v) {
    print(v.asString)
    return (consume (v))
}
x13.x := tap(x13.x := null)

print("\n\nTest 14")
var x14 := isoTest 14
var y14 := consume(x14)

print("\n\nTest 15")
var x15 := isoTest 15
x15.x := isoTest 15.1
let (consume(x15.x)) in { v -> v.asString }

print("\n\nTest 16")
var x16 := isoTest 16
x16.x := (x16.x := null)

print("\n\nTest 17")
var x17 := isoTest 17
var y17 := isoTest 17.1
print(x17.f(consume (y17)))
print(x17.f(consume (x17)))

print("\n\nTest 18")
var x18 := isoTest 18
print(x18.f(localTest 18.1))

//Should not work
print("\n\nTest 19")
var x19 := isoTest 19
//x19.x := x19.x


print("\n\nTest 20")
var x20 := object {
    use isolate
    method foo { return self }
}
//var y20 := x20.foo // or even x.foo?


print("\n\nTest 21")
var x21 := object {
    use isolate
    var x
    method f(v) {
        x := consume(v)
    }
}
//x21.f(localTest 21)

print("\n\nTest 22")
//(isoTest 22).x := localTest 22.1

print("\n\nTest 23")
var x23 := localTest 23
x23.x := localTest 23.1
x23.x.x := isoTest 23.2
//x23.x.x.x := (x23.x := null)

//Should succeed
print("\n\nTest 30") //Yes there is a jump in numbers
var x30 : iso := isoTest 30
x30 := isoTest 30.1

print("\n\nTest 31")
var x31 : local := localTest 31
def y31 : local = localTest 31.1

print("\n\nTest 32")
method foo32(y : iso) {
    y.x := "hello"
    consume(y)
}
var x32 := isoTest 32
x32 := foo32(x32 := null)

print("\n\nTest 33")
{ x : iso -> 5 }.apply(isoTest 33)

//Should fail
print("\n\nTest 34")
//var x34 : iso := localTest 34

print("\n\nTest 35")
//var x35 : local := object {}

print("\n\nTest 36")
method foo36(y : iso) { }
var x36 := localTest 36
//foo36(x36 := null)

//THREADS!
(spawn { x -> print(<- x.imm)}).imm <- 5


//Should work
print("\n\nTest 37")
def c37 = spawn { d -> d.imm <- 37 }
print(<- c37.imm)

print("\n\nTest 38")
def c38 = spawn { d ->
    var a := <- d.iso
    print(a)
}
c38.iso <- isoTest 38

print("\n\nTest 39")
def c39 = spawn { d ->
    var a := <- d.iso
    print(a.x)
    a.x := "world"
    d.iso <- consume(a)
}
var x39 := isoTest 39
x39.x := "hello"
c39.iso <- (x39 := null)
x39 := <- c39.iso
print(x39.x)

//Doesn't work
print("\n\nTest 40")
def c40 = spawn { d ->
    d.imm <- 5
    //d.iso <- isoTest 40
}
//print(<- c40.iso)
print(<- c40.imm)

//Pong
method isoPong {
    def c = spawn { d ->
        print "                                  Waiting (d)..."
        var a := <- d.iso
        print "                                  Got {a.msg}, sending..."
        a.msg := "magis"
        d.iso <- (a := null)
        print "                                  Waiting (d)..."
        a := <- d.iso
        print "                                  Got {a.msg}, sending..."
        a.msg := "desideranda"
        d.iso <- consume(a)
        print "                                  Done (d)"
    }
    var myIso := object {
        use isolate
        var msg is public := "sapienta"
    }
    print "Sending..."
    c.iso <- (myIso := null)
    print "Waiting (c)..."
    myIso := <- c.iso
    print "Got {myIso.msg}, sending..."
    myIso.msg := "auro"
    c.iso <- consume(myIso)
    print "Waiting (c)..."
    myIso := <- c.iso
    print "Got {myIso.msg}."
    print "Done (c)"
}
isoPong


// Doesn't work as isn't tied to  a list implementation
method primeGen(max) {
    def c = spawn { p ->
        def primes = _list
        def n = spawn { n ->
            var i := 2
            while {true} do {
                n.imm <- i
                i := i + 1
            }
        }
        while {true} do {
            def i = <- n.imm
            var isPrime := true
            for (primes) do { v -> if ((i % v) == 0) then { isPrime := false
} }
            if (isPrime) then {
                p <- i
                primes.Add(i)
            }
        }
    }
    var x := <- c
    while {x < max} do {
        print(x)
        x := <- c
    }
}
//primeGen(10)
