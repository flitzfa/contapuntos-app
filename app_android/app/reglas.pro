# ONNX Runtime se usa desde código nativo: si el optimizador le cambia los nombres, la app se cierra al contar.
-keep class ai.onnxruntime.** { *; }
