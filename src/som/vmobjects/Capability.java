package som.vmobjects;

public enum Capability {
  IMMUTABLE, ISOLATE, ALIASED_ISOLATE, LOCAL, UNSAFE;

  public boolean supports(final Capability vc) {
    return (this == ISOLATE || this == ALIASED_ISOLATE) && (vc == ISOLATE || vc == IMMUTABLE)
        ||
        this == LOCAL && vc != UNSAFE ||
        this == IMMUTABLE && (vc == ISOLATE || vc == IMMUTABLE) ||
        this == UNSAFE;
  }
}
