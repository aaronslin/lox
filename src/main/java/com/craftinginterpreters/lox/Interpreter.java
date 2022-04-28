package com.craftinginterpreters.lox;

class Interpreter {

}

/* 
Notes:
 - Here I execute an Expr tree. (As opposed to compiling to machine code or other options)
 - Lox is dynamically typed but Java is static!

Corrections:
0 / 0 (returns NaN, but should throw error)

Misleading error messages:
)
5 + "hi"
5 hi ("Cannot end with expression")
---- ("Must start with expression")

Feature differences:
 - the book's interpreter doesn't allow addition on booleans. 
 - the book's interpreter evaluates strings (even "") to true. Mine throws.
 - My interpreter lacks an interpret() method to map Java output values into
   Lox string that is shown to the user.
 - the book's interpreter uses the visitor pattern to write the evaluate()
   methods in the Interpreter, instead of scattering it into the Expr classes.
*/
