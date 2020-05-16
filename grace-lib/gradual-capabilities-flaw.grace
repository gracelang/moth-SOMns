var x := object {
    use isolate
    method f { }
    method g {
        object {
          method h { f }
        }
    }
}

def y = x.g
def c = spawn { v -> (<- v)}
c.iso <- consume(x)
print(y.h)
