package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Lexeme {
  public List<Lexeme> children;

  public void printClass(String prefix, String text) {
    System.out.printf(prefix + this.getClass().getSimpleName() + ": " + text + "%n");
  }

  public void print() {
    System.out.println(".");
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
