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

class ParseError extends LoxException {
  ParseError(String message) {
    super(message);
  }
}

class InterpreterCastException extends LoxException {
  public InterpreterCastException(String javaType, Object obj) {
    super(String.format("Cannot cast %s (type: %s) to %s.", obj, obj.getClass().getName(), javaType));
  }
}

class RuntimeError extends LoxException {
  final Token token;

  RuntimeError(Token token, String message) {
    super(message);
    this.token = token;
  }
}

class AssertionError extends LoxException {
  AssertionError(String message) {
    super(message);
  }
}
