package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Printable {
  public List<Printable> children;

  public void printClass(String prefix, String text) {
    System.out.printf(prefix + this.getClass().getSimpleName() + ": " + text + "%n");
  }

  public void print() {
    String caller = Thread.currentThread().getStackTrace()[2].getMethodName();
    System.out.println(caller);
    print("", true);
  }

  public void print(String prefix, boolean parentIsYoungest) {
    String parent_postfix = parentIsYoungest ? "└─" : "├─";
    printClass(prefix + parent_postfix, toString());

    for (int i=0; i<children.size(); i++) {
      String child_postfix  = parentIsYoungest ? "  " : "│ ";
      children.get(i).print(prefix + child_postfix, (i==children.size()-1));
    }
  }
}

// (alin) This distinction between Lexeme and Printable isn't needed
// but I want to use the print class for Statements, which conceptually aren't lexemes
// but also don't want to refactor out Lexeme's yet.
abstract class Lexeme extends Printable {}

class Token extends Lexeme {
  final TokenType type;
  final String lexeme;
  final Object literal;
  final int line; // [location]

  Token(TokenType type, String lexeme, Object literal, int line) {
    this.type = type;
    this.lexeme = lexeme;
    this.literal = literal;
    this.line = line;
    this.children = Arrays.asList();
  }

  public String fullString() {
    return "[" + lexeme + ": " + type + "]";
  }

  public String toString() {
    return "" + lexeme;
  }
}
