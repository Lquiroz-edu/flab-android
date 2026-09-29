# Referencia aportada: Duo Fold Live 3.5.2

Este directorio conserva el contenido completo del ZIP `duo-fold-live-ci-63-1.zip` aportado por Luis, sin modificar sus archivos. El APK adjunto se inspeccionó por separado y no se añade al repositorio.

- [Análisis e integración en F/LAB](../../docs/DUO_FOLD_REFERENCE.md).
- [Inventario, hashes y datos del APK](PROVENANCE.json).
- [README original](source/README.md), [licencia MIT](source/LICENSE) y [avisos de terceros](source/THIRD_PARTY_NOTICES.md).

`source/` es un proyecto Android independiente de referencia. No está incluido en `settings.gradle.kts`, no se compila dentro de F/LAB y sus servicios y permisos no forman parte de nuestro APK. Sus workflows anidados tampoco son workflows de F/LAB. Contiene los JAR, wrapper, recursos, tests y documentación del archivo original para que una adaptación posterior tenga trazabilidad y conserve atribuciones.

El README del ZIP identifica el proyecto como `joeconsorti/duo-fold-live`; el comentario del archivo identifica el commit `8ca4371bf4c83ea6c840a2d500325e55635245be`. Son datos de la entrega, no una verificación independiente de su procedencia. El manifiesto binario del APK coincide con la versión y applicationId del código; eso no demuestra que el binario se construyera exactamente con estos fuentes.

Para estudiar o compilar el proyecto independiente, seguir su README y revisar su configuración. Para integrar funcionalidad en F/LAB, seguir primero el plan de adaptación. No copiar applicationId, autoridades de providers, tokens Binder o claves de preferencias sin diseñar su aislamiento.
