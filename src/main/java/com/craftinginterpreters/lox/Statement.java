package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

abstract class Statement extends Printable {
  abstract public Void executeWith(Visitor<Void> visitor);
  
  interface Visitor<T> {
    public T execExprStmt(ExprStmt stmt);
    public T execPrintStmt(PrintStmt stmt);
    public T execAssignStmt(AssignStmt stmt);
    public T execBlockStmt(BlockStmt stmt);
  }
}

class ExprStmt extends Statement {
  ExprStmt(Expr expr) {
    this.expr = expr;
    this.children = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execExprStmt(this);
  }
}

class PrintStmt extends Statement {
  PrintStmt(Expr expr) {
    this.expr = expr;
    this.children = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execPrintStmt(this);
  }
}

class AssignStmt extends Statement {
  AssignStmt(String name, Variable variable) {
    this.name = name;
    this.variable = variable;
    // (alin) commenting out scope for now -- not sure if this belongs to stmt
    // or the interpreter
    // this.scope = scope;
    // (alin) to revisit
    this.children = Arrays.asList(variable);
  }

  final String name;
  final Variable variable;
  // final Scope scope;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execAssignStmt(this);
  }
}

class BlockStmt extends Statement {
  BlockStmt(List<Statement> statements) {
    this.statements = statements;
  }

  final List<Statement> statements;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execBlockStmt(this);
  }
}

// (alin) should eventually handle reference counting for the closure case
class Scope extends Printable {
  Scope(Scope parent) {
    this.parent = parent;
    this.locals = new HashMap<>();
    this.children = Arrays.asList();
  }

  final Scope parent;
  final Map<String, Variable> locals;

  public Variable get(String name) {
    this.printScope("");
    if (locals.containsKey(name)) {
      return locals.get(name);
    }

    if (parent == null) {
      throw new VariableException(String.format("Variable '%s' is not defined.", name));
    }
    return parent.get(name);
  }

  public void set(String name, Variable variable) {
    locals.put(name, variable);
    // increment variable.numReferences and decrement oldVar
  }

  public Scope getGlobal() {
    if (this.parent == null) {
      return this;
    }
    return parent.getGlobal();
  }

  public void printScope(String prefix) {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    System.out.println(caller.getClassName() + "." + caller.getMethodName());
    System.out.println(prefix + "{");
    for (Printable child : children) {
      child.print(prefix + "  ", true);
    }
    for (String key : locals.keySet()) {
      Variable v = locals.get(key);
      System.out.println(prefix + "  " + key);
      v.print(prefix + "  ", true);
    }
    System.out.println(prefix + "}");
  }
}

class Variable extends Printable {
  Variable(Expr expr) {
    // Assign to the expression value, not the expression tree. Otherwise:
    //   var bar = 1;
    //   var foo = bar + 1;
    //   bar = 2;
    //   assert foo == 2;
    this.expr = expr;
    this.children = Arrays.asList(expr);
  }

  // (alin) should this be final?
  final Expr expr;
}

class VariableException extends RuntimeException {
  String code;
  int column;

  public VariableException(String message, String code, int column) {
    super(message);
    this.code = code;
    this.column = column;
  }

  public VariableException(String message) {
    this(message, "", -1);
  }
}

/*
Scope: 
  Test case 1:
  var foo = 1;
  var bar = Cat();
  
  fun f(x, y) {
    assert x.addr != foo.addr
    assert y.addr == bar.addr
  }

  Test case 2: [different ways of setting].
  var a = 1;
  var b = Cat();
  var c = func();
  var d = a;  // should "evaluate" a, set equal to the variable's value
  var e = b;  // should set to same object as b
  var f = c;  // should not re-evaluate func.

  Test case 3:
  a = 1;
  if (...) {
    a=2;
    b=3;
  }
  func f () {
    a=2;
    b=3;
  }
  {
    a=2;
    b=3;
  }
  class Cat {
    a=2;
    b=3;
  }

Variables:
  Test case 1:
  var bar = 2;
  var foo = bar;
  var foo = 1;
  assert bar == 2;

  Test case 2:
  var bar = 2;
  var foo = bar;
  var bar = 1;
  assert foo == 2;  // 2 is primitive

  Test case 3:
  var bar = Cat(2);
  var foo = bar;
  var bar.age += 1;
  assert foo.age == 3;  // bar is object
  */

/*
program        → statement* EOF ;

statement      → exprStmt
               | printStmt 
               | assignStmt ;

exprStmt       → expression ";" ;
printStmt      → "print" expression ";" ;
assignStmt    -> var? literal (equal expression)?;
*/
