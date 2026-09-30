# Prueba Duo en F/LAB 0.7.0

## Cómo probarla

1. Abre F/LAB y entra en **Fold Motion** desde Home. La primera tarjeta es **Duo · prueba de cristal**.
2. En **Interior**, mueve el ángulo entre 90° y 180°: la parte izquierda del contenido se proyecta y desenfoca; al abrir vuelve a su estado plano.
3. En **Exterior**, mueve el ángulo entre 0° y 110° para ver la otra curva. **Abrir y cerrar** reproduce un recorrido manual; **Detener** o arrastrar el slider lo interrumpe.
4. Ajusta **Desenfoque** y compara con 0%. El movimiento cambia las coordenadas del contenido, además de oscurecerlo.
5. Opcionalmente activa el motor F/LAB y Fold Motion, pulsa **Probar con bisagra** y mueve físicamente el teléfono. La tarjeta muestra las muestras del sensor recibidas por el Core. Un sensor presente no implica ángulos continuos; hasta observar al menos tres valores intermedios distintos se indica continuidad sin demostrar y no se suavizan sus pasos.

La prueba manual no requiere accesibilidad, overlay, cambiar wallpaper ni Shizuku. Esta build conserva los módulos anteriores, pero no hace falta activarlos para usar el laboratorio local. La opción de bisagra mantiene una solicitud de tracking al Core solo mientras esa tarjeta está visible y la Activity está STARTED; se libera al volver atrás, cambiar a manual o salir de la app.

## Qué se está validando

El renderer adapta `ClassicGlassShader.kt` y `FrameSmoothing.kt` de Duo Fold Live 3.5.2. Dibuja una textura de contenido local con una pirámide de seis niveles; el shader GPU utiliza perspectiva, curvas V2, blur espacial y oscurecimiento. No es la ruta completa de Windowed Glass ni la captura de otras apps.

Requiere Android 13+ con canvas acelerado. Un fallo del shader conserva el contenido plano y muestra ese estado. Las licencias y avisos originales se incluyen en `app/src/main/assets/licenses/duo/`.

Esta primera prueba no integra el canal de ángulos Samsung/Shizuku, el mirror de la pantalla exterior, la captura privilegiada, la compresión vertical del shader más reciente ni el handoff entre paneles. El siguiente paso para seguir la bisagra con ángulos continuos en el Fold del usuario es validar y adaptar el canal Samsung/Shizuku descrito en la referencia.

## Comprobaciones en el teléfono

- Parar y retroceder a mitad del slider: el efecto debe seguir el nuevo destino sin completar una animación antigua.
- Abrir/cerrar, detener y cambiar de panel durante la reproducción: no deben quedar callbacks de reproducción activos.
- Pasar a otra app y volver: la reproducción se detiene; el tracking se libera en segundo plano.
- Girar el teléfono y cambiar de tamaño: el contenido de la tarjeta se regenera para su geometría.
- En modo sensor, distinguir ángulos medidos de simulación manual; no interpretar muestras discretas como continuidad real.

Tests unitarios cubren límites de las curvas, rechazo de muestras no válidas, clasificación conservadora y reversión del smoothing. La comprobación física del acabado visual, jank y batería queda pendiente en el dispositivo objetivo.

## Base de desarrollo

Esta prueba parte de `49099d4`, la rama `claude/flab-v1-definition-done-e8ag34`, que contiene el Core, Home y Fold Motion más recientes del repositorio. Incorpora también los fuentes de referencia de la PR #3; no sustituye la aplicación por la base antigua de `main`.
