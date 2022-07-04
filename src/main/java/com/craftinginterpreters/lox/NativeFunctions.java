package com.craftinginterpreters.lox;

import java.util.List;

class NativeFunctions {
  public static LoxCallable CLOCK = new LoxCallable() {
    @Override
    public int arity() { return 0; }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
      return (double)System.currentTimeMillis() / 1000.0;
    }

    @Override
    public String toString() { 
      return "<native fn: clock>"; 
    }
  };

  public static LoxCallable ASSERT = new LoxCallable() {
    @Override
    public int arity() { return 1; }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
      try {
        if (!Interpreter.toBool(arguments.get(0))) {
          throw new AssertionError("Assertion is false.").withInterpreterState(interpreter);
        }
      } catch (LoxException e) {
        // This should never happen.
        throw new AssertionError(String.format("Assertion threw: \"%s\".", e.getMessage()))
          .withInterpreterState(interpreter);
      }
      return null;
    }

    @Override
    public String toString() { 
      return "<native fn: assert>"; 
    }
  };

  public static LoxCallable ASSERT_RAISES = new LoxCallable() {
    @Override
    public int arity() { return 1; }

    @Override
    public boolean isValidArity(int numArgs) { return numArgs >= arity(); }

    @Override
    public String arityString() { return arity() + "+"; }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
      if (!(arguments.get(0) instanceof LoxCallable)) {
        throw new AssertionError("Expect signature: assert_raises(function, args...)")
          .withInterpreterState(interpreter);
      }
      LoxCallable target_func = (LoxCallable) arguments.get(0);
      List<Object> target_args = arguments.subList(1, arguments.size());

      if (!target_func.isValidArity(target_args.size())) {
        throw new AssertionError(
          String.format(
            "Expected %s arguments to %s, but got %s.", 
            target_func.arity(), 
            target_func, 
            target_args.size()
          )
        ).withInterpreterState(interpreter);
      }

      interpreter.callStack.push(target_func);
      try {
        Object value = target_func.call(interpreter, target_args);
      } catch (LoxException e) {
        return null;
      } finally {
        interpreter.callStack.pop();
      }
      throw new AssertionError("Expected an exception, but none were thrown.")
        .withInterpreterState(interpreter);
    }

    @Override
    public String toString() { 
      return "<native fn: assert_raises>"; 
    }
  };
}
