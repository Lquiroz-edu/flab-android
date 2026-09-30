# Duo Fold Live: qué aporta a F/LAB

## Entrega y alcance de la revisión

Se revisaron el ZIP con código completo y el manifiesto binario del APK aportados por Luis. El APK declara `org.duofold.live`, versión `3.5.2`, código `1071`, minSdk `34` y target/compileSdk `36`; coincide con `source/app/build.gradle.kts`. No se ha instalado en un teléfono, verificado su firma ni reproducido su compilación. El nombre `ci-63-1` del archivo no cambia la versión declarada.

Los [fuentes completos](../references/duo-fold-live/source/README.md) quedan conservados como referencia independiente, con [inventario SHA-256](../references/duo-fold-live/PROVENANCE.json), licencias y tests. La importación original añade conocimiento y material reutilizable. La build 0.7.0 adapta ahora el shader Classic y su smoothing para una [prueba local](DUO_PREVIEW_TEST.md), sin integrar el motor privilegiado completo.

## Cómo consigue el efecto

| Etapa | Archivos originales, dentro de `references/duo-fold-live/source/` | Comportamiento observado en código |
| --- | --- | --- |
| Ángulo continuo | `app/src/main/java/org/duofold/live/LiveAngles.java`, `AngleReader.java`, `AngleParser.java` | Una ventana de 1×1 con `FLAG_SHOW_WALLPAPER` envía comandos al wallpaper Samsung. Un helper Shizuku lee respuestas filtradas de logcat con `mCurrentAngle`. |
| Estado y recuperación | `LiveAngles.java`, `PollCadence.java`, `ReaderRecovery.java` | Binder en un worker, una petición en vuelo, frescura y reconexión. Objetivo de sondeo de 4 ms con pantalla interactiva; 500 ms sin ella. No son tasas garantizadas. |
| Captura | `GlassCapture.java`, `GlassFrames.kt`, `GlassFramePolicy.java` | Captura privilegiada por WindowManager, exclusión del propio overlay, comprobación de bloqueo, tamaño y panel. Rechaza capas seguras. |
| Proyección visual | `DuoGlass.kt`, `ClassicGlassShader.kt`, `FrameSmoothing.kt`, `DuoShadeCurve.kt` | Contenido proyectado, reflejo, desenfoque, oscurecimiento y suavizado temporal acotado. Controles distintos para pantalla exterior e interior. |
| Preview y cambio de panel | `InnerLiveMirror.java`, `PreviewExpansion.java`, `CoverHandoff.java`, `DirectHandoffPolicy.java`, `HandoffFade.java` | Preview de la pantalla exterior dentro de la interior, coordinación de estados concurrentes y fundido alrededor del cambio nativo. |
| Alojamiento fuera de la app | `StandaloneService.kt`, `FoldBackgroundService.java`, `app/src/main/res/xml/accessibility.xml` | Servicio de accesibilidad para alojar overlays y servicio foreground para supervisión y recuperación. No sustituye el launcher. |
| Configuración y diagnóstico | `SetupActivity.kt`, `WallpaperSetupService.java`, `FrameTelemetry.kt`, `HealthTrace.java` | Verificación del wallpaper, permisos, recuperación y métricas separadas de captura y renderizado. |

En la base inicial `main`, la fuente pública de postura de F/LAB (`FoldSnapshot.kt`) informa `FLAT`/`HALF_OPENED` y geometría, sin un ángulo continuo. El `HingeTracker.kt` de la entrega documenta sensores con valores discretos y evita presentar su interpolación como medición real. El canal wallpaper/Shizuku es el mecanismo específico que debe comprobarse en el dispositivo del usuario.

`AngleReader` rechaza respuestas logcat de más de 1.500 ms o con fecha futura fuera de tolerancia. El motor considera fresco el ángulo durante menos de 750 ms. `GlassFramePolicy` exige una captura de hasta 350 ms y dimensiones coincidentes. Son umbrales del proyecto de referencia, no presupuestos de latencia ya validados para F/LAB.

**Detalle importante:** aunque el documento original `PRIVACY.md` menciona captura de accesibilidad, `accessibility.xml` declara `canTakeScreenshot=false` y `canRetrieveWindowContent=false`, y `StandaloneService.requestCapture()` no captura píxeles. El camino de captura revisado usa el helper `GlassCapture` de Shizuku. La integración debe basarse en este código y en pruebas reales.

## Requisitos y límites

- Sin root no significa sin privilegios: requiere Shizuku en modo ADB y su autorización, overlay, accesibilidad y el wallpaper interactivo Samsung adecuado. La instalación del proyecto de referencia configura bindings HOME de wallpaper; no debe trasladarse a F/LAB sin un flujo explícito de activación y restauración.
- El README declara pruebas únicamente en SM-F971U. El SM-F971B del usuario necesita validación propia. Reconocer un modelo no prueba compatibilidad de firmware o APIs ocultas.
- MinSdk 34 no demuestra soporte completo: el README requiere Android 17 y comprobaciones Samsung. Separar requisitos del manifiesto, comprobaciones de ejecución y compatibilidad demostrada.
- El espejo no equivale a dos apps independientes activas en ambos paneles. Tampoco demuestra que cualquier app conserve su Activity o su reproducción durante el cambio.
- El fundido suaviza el apagado durante el cambio de panel, sin demostrar su eliminación. [La inspección de firmware incluida](../references/duo-fold-live/source/docs/FIRMWARE-DISPLAY-FINDINGS.md) describe límites del mapeo concurrente, foco y configuración nativa; sus conclusiones corresponden al firmware examinado por el autor.
- 60/120 FPS son objetivos seleccionables. Medir captura, envío al renderer y fotogramas visibles por separado. El proyecto usa servicios persistentes y sondeo frecuente; bajo consumo queda por demostrar.
- Shizuku puede necesitar reinicio tras reboot. Bloqueo de pantalla, permisos revocados, helper desconectado, captura protegida y cambio de geometría deben detener o degradar el efecto limpiamente.

## Adaptación propuesta

| Paso | Trabajo concreto en F/LAB | Evidencia para pasar al siguiente paso |
| --- | --- | --- |
| 1. Capability Probe | Fuentes independientes de postura, sensor público y canal Samsung/Shizuku; exponer origen, ángulo raw, edad, variación y errores. | En SM-F971B, distinguir mediciones intermedias reales de 0/90/180 y de ausencia de datos. Repetir tras reboot y reconexión. |
| 2. Core único | Sesión con postura, dirección, ángulo raw/visual, orientación, tamaño, display activo, foreground, permisos, perfil y módulos. Timestamps monotónicos y descarte de generaciones anteriores. | Abrir → invertir a mitad → cerrar → reabrir sin estado viejo, saltos inventados ni pérdida de sesión. |
| 3. Renderer local | Adaptar matemáticas de proyección y smoothing con contenido de F/LAB, conservando avisos de licencia. | Medir estabilidad, insets, rotación y geometría; controles interior/exterior y perfil de ahorro. |
| 4. Overlay opt-in | Adaptadores aislados para captura, mirror y handoff. Activación reversible y limpieza de surfaces, buffers, orientación y ajustes modificados. | Home, Instagram y YouTube con pruebas de cierre, permisos revocados, pantalla bloqueada y ahorro de batería. No prometer continuidad universal. |
| 5. Ajuste medido | Baseline de 60 FPS; habilitar calidad mayor tras medición. Pausar captura fuera de transición y reducir trabajo estable cuando el canal lo permita. | Comparar consumo con módulo apagado, frame times, latencia de ángulo, blackout visible y recuperación. |

La primera prueba local del renderer ya se adapta en la build 0.7.0. La base de desarrollo más reciente dispone de un Core único y de input de sensor público, cuya continuidad debe comprobarse. La integración privilegiada pendiente comienza por el probe de ángulo Samsung/Shizuku. Mantener los cambios privilegiados en adaptadores para que una pérdida del canal Samsung deje disponible la experiencia estándar de F/LAB.

## Reutilización y atribución

La entrega contiene licencia MIT de Duo Fold Live y atribuciones a Folio/Duo Launcher, FoldUO y la referencia `chuspeeism/iphone-duo`, además de Shizuku y HiddenApiBypass. Se conservan `LICENSE`, `THIRD_PARTY_NOTICES.md`, `upstream/` y `app/src/main/assets/licenses/`. Si código pasa al APK de F/LAB, incluir las atribuciones correspondientes también en el APK distribuido.

No se incluyen el APK de 20 MB, activos Samsung externos ni nuevas claves de firma. No se modifican los fuentes de la entrega. La compilación y los permisos de F/LAB siguen definidos por su propio módulo `app`.
