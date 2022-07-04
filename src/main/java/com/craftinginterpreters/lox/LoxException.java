package com.craftinginterpreters.lox;

class LoxException extends RuntimeException {
  LoxException(String message) {
    super(message);
  }
}

// This is NOT a LoxException
class ReturnException extends RuntimeException {
  public ReturnException(Object value) {
    this.value = value;
  }

  Object value;
}

// This is NOT a LoxException
class JavaError extends RuntimeException {
  public JavaError(Statement statement, RuntimeException error) {
    this.error = error;
    this.statement = statement;
  }
  RuntimeException error;
  Statement statement;
}

class ParseError extends LoxException {
  ParseError(String message) {
    super(message);
  }
}

// class InterpreterException extends LoxException {
//   InterpreterException(String message) {
//     super(message);
//   }
// }

class InterpreterCastException extends LoxException {
  public InterpreterCastException(String javaType, Object obj) {
    super(String.format("Cannot cast %s (type: %s) to %s.", obj, obj.getClass().getName(), javaType));
  }
}

class EnvironmentException extends LoxException {
  EnvironmentException() {
    super("Variable not found.");
  }
}

class RuntimeError extends LoxException {
  final Token token;
  DebugInfo debugInfo;

  RuntimeError(Token token, String message) {
    super(message);
    this.token = token;
  }

  public RuntimeError withInterpreterState(Interpreter interpreter) {
    this.debugInfo = new DebugInfo(interpreter);
    return this;
  }
}

class AssertionError extends LoxException {
  DebugInfo debugInfo;

  AssertionError(String message) {
    super(message);
  }

  public AssertionError withInterpreterState(Interpreter interpreter) {
    this.debugInfo = new DebugInfo(interpreter);
    return this;
  }
}
