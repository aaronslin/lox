package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

abstract class Statement extends Printable {
  abstract public Void executeWith(Visitor<Void> visitor);
  
  interface Visitor<T> {
    public T execBlockStmt(BlockStmt stmt);
    public T execExprStmt(ExprStmt stmt);
    public T execForStmt(ForStmt stmt);
    public T execIfStmt(IfStmt stmt);
    public T execPrintStmt(PrintStmt stmt);
    public T execVarStmt(VarStmt stmt);
    public T execWhileStmt(WhileStmt stmt);
  }
}

class ExprStmt extends Statement {
  ExprStmt(Expr expr) {
    this.expr = expr;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execExprStmt(this);
  }
}

class PrintStmt extends Statement {
  PrintStmt(Expr expr) {
    this.expr = expr;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execPrintStmt(this);
  }
}

class VarStmt extends Statement {
  VarStmt(Token name, Expr expr) {
    this.name = name;
    this.expr = expr;
    this._printables = Arrays.asList(expr);
  }

  final Token name;
  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execVarStmt(this);
  }
}

class BlockStmt extends Statement {
  BlockStmt(List<Statement> statements) {
    this.statements = statements;
    this._printables = statements;
  }

  final List<Statement> statements;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execBlockStmt(this);
  }
}

class WhileStmt extends Statement {
  WhileStmt(Expr condition, Statement body) {
    this.condition = condition;
    this.body = body;
    this._printables = Arrays.asList(condition, body);
  }
  
  final Expr condition;
  final Statement body;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execWhileStmt(this);
  }
}

class ForStmt extends Statement {
  ForStmt(
    Statement initializer, 
    Expr condition, 
    Statement iterator, 
    Statement body
  ) {
    this.initializer = initializer;
    this.condition = condition;
    this.iterator = iterator;
    this.body = body;
    this._printables = Arrays.asList(initializer, condition, iterator, body);
  }

  final Statement initializer;
  final Expr condition;
  final Statement iterator;
  final Statement body;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execForStmt(this);
  }
}

class IfStmt extends Statement {
  IfStmt(Expr condition, Statement then, Statement otherwise) {
    this.condition = condition;
    this.then = then;
    this.otherwise = otherwise;
    this._printables = Arrays.asList(condition, then, otherwise);
  }

  final Expr condition;
  final Statement then;
  final Statement otherwise;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execIfStmt(this);
  }
}


// (alin) should eventually handle reference counting for the closure case
class Scope extends Printable {
  Scope(Scope parent) {
    this.parent = parent;
    this.locals = new HashMap<>();
    this._printables = Arrays.asList();
  }

  final Scope parent;
  final Map<String, Variable> locals;

  public Variable get(Token token) {
    String name = token.literal.toString();
    if (locals.containsKey(name)) {
      return locals.get(name);
    }

    if (parent == null) {
      throw new VariableException(token, "Variable not defined.");
    }
    return parent.get(token);
  }

  public void declare(Token token, Variable variable) {
    locals.put(token.literal.toString(), variable);
    // increment variable.numReferences and decrement oldVar
  }

  public void assign(Token token, Variable variable) {
    String name = token.literal.toString();
    if (locals.containsKey(name)) {
      locals.put(name, variable);
    } else if (parent == null) {
      throw new VariableException(token, "Undeclared variable cannot be assigned to.");
    } else {
      parent.assign(token, variable);
    }
  }

  public Scope getGlobal() {
    if (this.parent == null) {
      return this;
    }
    return parent.getGlobal();
  }

  @Override
  public void print() {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    System.out.println(caller.getClassName() + "." + caller.getMethodName());
    printScope("");
  }

  public void printScope(String prefix) {
    System.out.println(prefix + "Scope {");

    for (String key : locals.keySet()) {
      Variable v = locals.get(key);
      System.out.println(prefix + "  " + key);
      v.print(prefix + "  ", true);
    }

    if (parent != null) {
      parent.printScope(prefix + "  ");
    }
    System.out.println(prefix + "}");
  }
}

class Variable extends Printable {
  Variable(Object value) {
    // Assign to the expression value, not the expression tree. Otherwise:
    //   var bar = 1;
    //   var foo = bar + 1;
    //   bar = 2;
    //   assert foo == 2;
    this.value = value;
    this._printables = Arrays.asList();
  }

  // (alin) should this be final?
  final Object value;

  public String toString() {
    return "" + value + " (" + value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value)) + ")";
  }
}

class VariableException extends RuntimeError {
  public VariableException(Token token, String message) {
    super(token, message);
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
