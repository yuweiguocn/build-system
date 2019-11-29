; This file will be enhanced by both InstantRun instrumentation visitors to make sure we handle
; correctly the JSR/RET byte codes correctly.
;
; There is no test code directly testing the result of the instrumentations, we are testing the
; ASM instrumentation only.
;
; see https://code.google.com/p/android/issues/detail?id=220019
; the byte below could be generated for this java code :
; public class JsrRetUser {
;    static {
;        System.out.println("HelloWorld");
;    }
;    public static void main(String[] args) {
;        try {
;            bodyOfTry();
;        }
;        finally {
;            bodyOfFinally();
;        }
;    }
; javac compiler is not supposed to generate this types of bytecodes any longer.
.bytecode 50.0
.source                  JsrRetUser.java
.class                   public com/jasmin/JsrRetUser
.super                   java/lang/Object

.method public static <clinit>()V
   .limit stack 2
   .limit locals 1
   ; Intentionally insert JSR instruction, even though it is pointless
   jsr LABEL_0
LABEL_0:
   getstatic java/lang/System/out Ljava/io/PrintStream;
   ldc "Hello World!"
   invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V
   return
.end method

.method                  public static main([Ljava/lang/String;)V
   .limit stack          1
   .limit locals         3
   .line                 4
   .var 0 is args [Ljava/lang/String; from L0 to L9
   .catch java/lang/Exception from L0 to L1 using L1
   .catch java/lang/Exception from L2 to L3 using L1
L0:
   .line 4
   invokestatic JsrRetUser/bodyOfTry()V
L4:
   .line 5
   goto L2
L1:
   .line 6
   astore_2
; JSR jumps to the finally block
   jsr L5
L6:
   .line 8
   aload_2
   athrow
L5:
   .line 6
   astore_1
L7:
   .line 7
   invokestatic JsrRetUser/bodyOfFinally()V
L8:
   .line 8
; RET returns from the finally block
   ret 1
L2:
; Jump to the same finally block from another execution path
   jsr L5
L3:
   .line 9
   return
L9:
.end method