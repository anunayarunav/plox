javac -d classes src/com/craftinginterpreters/lox/*.java;
java -cp classes/ com.craftinginterpreters.lox.Lox $1 $2
