def c = spawn { d ->
  while {true} do {
    var tmp := <- d.iso
    tmp.value := tmp.value + 3
    d.iso <- consume(tmp)
  }
}

method modify(v : !iso, m) -> !iso {
  v.value := v.value * m
  return consume(v)
}

var ball := object {
  use isolate
  var value is public := 1
}

for (1 .. 10) do { i ->
  c.iso <- modify(consume(ball), i)
  ball := <- c.iso
  print(ball.value)
}