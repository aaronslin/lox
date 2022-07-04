package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Stack;

class DebugInfo {
  // Eventually, this should include evalStack too.
  final Stack<Statement> executionStack;

  // This and the environment should be reset in finally blocks
  // but snapshotted at the place the error is thrown.
  final Stack<LoxCallable> callStack;

  // Not sure how to populate this yet to inspect the variables.
  final Scope environment;


  DebugInfo(Interpreter interpreter) {
    this.executionStack = new Stack<Statement>();
    this.callStack = new Stack<LoxCallable>();
    this.environment = interpreter.currentScope.copyValues();

    this.executionStack.addAll(interpreter.executionStack);
    this.callStack.addAll(interpreter.callStack);
  }
}
