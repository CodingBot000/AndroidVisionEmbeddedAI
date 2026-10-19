-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
# Optional MediaPipe graph-template/profiler protobuf types are absent in the official tasks AAR.
# ObjectDetector does not call these APIs; keep suppression restricted to the two optional types.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
# Flogger's forEnclosingClass inspects caller stack frames. R8 inlining/renaming
# otherwise makes MediaPipe Graph's static logger throw "no caller found".
-keep class com.google.common.flogger.** { *; }
-keepattributes SourceFile,LineNumberTable
# MediaPipe uses protobuf Any and other well-known types through encoded field
# names. Preserve their fields/classes so native graph config parsing survives R8.
-keep class com.google.protobuf.** { *; }
