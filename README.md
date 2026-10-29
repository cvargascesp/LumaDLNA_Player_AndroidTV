# Luma DLNA para Android TV

Cliente nativo Kotlin para Android 8 o superior, con interfaz para mando y pantallas táctiles.

## Uso

Al abrir muestra los servidores guardados y busca servidores DLNA mediante SSDP en la red local. Abre un servidor, navega por sus carpetas y pulsa un video para reproducir. La galería presenta carátula, título, duración, resolución y formato cuando el servidor entrega esos metadatos. Sin carátula se usa una imagen de respaldo.

El reproductor Media3 ofrece barra de progreso, pausa, anterior/siguiente y selección de pistas con «Audio» y «Subtítulos». Solo se muestran pistas disponibles y compatibles. La cola incluye los videos de la carpeta en el orden del servidor, empieza por el seleccionado y avanza automáticamente. Al terminar el último vuelve al primero para mantener reproducción continua.

Cada video guarda su posición localmente cada segundo y al pausar, cerrar el reproductor o salir de la aplicación. Al abrirlo otra vez continúa desde esa posición. Cuando llega al final, se elimina el progreso para que la próxima reproducción comience desde el inicio. El progreso se identifica por servidor y archivo DLNA.

Abre una carpeta y pulsa «Guardar acceso» para añadir un acceso directo al inicio. Si ya está guardada, el mismo botón muestra «Eliminar acceso» y permite quitarla con una pulsación normal de OK. El botón actualiza su estado al guardar o eliminar. Mantener OK en una carpeta también permite guardarla. Atrás cierra el reproductor o sube de carpeta. Al salir de la aplicación se pausa la reproducción.

El menú lateral «Bibliotecas» está disponible en todas las pantallas de navegación, excepto reproducción. Incluye accesos guardados y servidores. Se abre con el botón de cabecera, la tecla Menú o Izquierda desde la primera columna de la galería. Atrás, Derecha, Cerrar o tocar fuera lo ocultan y restauran el foco. Mantén OK sobre un acceso del menú para quitarlo.

Durante reproducción, con controles ocultos o con la barra de progreso enfocada, Izquierda/Derecha adelantan o retroceden 5 segundos. Mantener la tecla actualiza una vista previa cada 180 ms tras una espera inicial de 450 ms: toque corto de 5 segundos, pasos de 10 segundos al mantener hasta 2 segundos, del 1 % de la duración entre 2 y 5 segundos y del 3 % desde los 5 segundos (mínimo 10 segundos y máximo 5 minutos por paso). Una nueva pulsación reinicia la aceleración. Al soltar se realiza una sola búsqueda a la posición elegida; Atrás cancela. Se respetan inicio y fin del video. Al mostrar los controles, el foco comienza en la barra de progreso, que se distingue únicamente por el círculo turquesa al tener foco o el cursor encima; la línea sigue blanca, sin fondo ni contorno. Arriba pasa a los iconos; Abajo desde los iconos vuelve a la barra. Audio y Subtítulos están debajo de la barra, en la fila inferior: Abajo desde la barra pasa a Audio, Derecha pasa a Subtítulos y Arriba regresa a la barra. Con los botones del reproductor enfocados, el D-PAD conserva la navegación entre controles. Los controles se ocultan tras 5 segundos de inactividad, incluso con la barra enfocada o el video pausado; usar el mando, tocar o mantener una búsqueda reinicia el plazo. Atrás los oculta y otro Atrás cierra el reproductor. Salir de la aplicación, cambiar de video o perder foco cancela una búsqueda pendiente.

## Compilar

El repositorio incluye Gradle Wrapper 9.3.0. Abrir la carpeta en Android Studio e instalar Android SDK Platform 36. Configurar un JDK compatible con el wrapper (se recomienda usar el JBR de Android Studio); el proyecto genera bytecode Java 17. Sincronizar y ejecutar el módulo app en Android o Android TV. Los comandos de compilación se detallan más abajo.

## Rendimiento y límites

La identidad del servidor se obtiene de su UDN/UUID UPnP para agrupar anuncios de distintas interfaces de red. Los registros antiguos de Plex se migran usando el UUID presente en su URL de ContentDirectory. Dos dispositivos con el mismo nombre siguen siendo servidores distintos si tienen UDN diferentes. Los accesos guardados se resuelven contra la dirección descubierta actualmente. La interfaz utiliza tarjetas con ilustraciones vectoriales locales para servidores/carpetas y carátulas del servidor para videos, controles con foco destacado y un menú lateral por secciones.

Servidores y carpetas se guardan localmente; los accesos abren directamente su carpeta y actualizan en segundo plano. No hay confirmaciones previas a reproducción. Objetivo de producto: inicio de reproducción en unos dos segundos en rutas guardadas, sujeto a disponibilidad del servidor, latencia, tamaño de bibliotecas y códec. No es una garantía de latencia ni una medición realizada.

Primera carga de carpetas solicita páginas de 200 elementos hasta completar el listado. El descubrimiento es IPv4 SSDP y necesita que el router permita multicast entre dispositivos. No incluye transcodificación, credenciales de servidor, subtítulos externos ni servicio de reproducción en segundo plano. Las pistas embebidas y los formatos dependen del dispositivo y Media3. La caché puede mostrar servidores desconectados; la interfaz informa si no puede actualizar.

## Verificación pendiente en equipo Android

- Servidor real (Jellyfin DLNA, Universal Media Server o equivalente): descubrimiento, carpetas con más de 200 elementos, carátulas y caracteres XML.
- Mando: foco visible, desplazamiento en galería, Atrás, controles, selector de pistas y pulsación larga.
- Archivo con varias pistas de audio/subtítulos; avance de video y vuelta del último al primero.
- Servidor desconectado, red sin multicast, archivo incompatible y restauración desde caché.
- Medir arranque frío y caliente hasta primer fotograma en el dispositivo de destino.

Verificación de compatibilidad con Plex: la petición Browse sin declaración XML ni `s:encodingStyle` devolvía HTTP 500 (Bad Request). El sobre SOAP completo devuelve correctamente las tres carpetas raíz. Tras corregirlo se compiló `assembleDebug` y se instaló en el emulador Android TV, donde la aplicación mostró Video, Music y Photos. Los errores de lectura ahora incluyen la causa HTTP/SOAP y se registran en Logcat con la etiqueta `LumaDLNA`. Reproducción y latencia hasta el primer fotograma siguen pendientes de validación.


## Requisitos y versiones

- Android 8.0 (API 26) o superior; interfaz horizontal para TV y dispositivos táctiles.
- SDK de compilación 36; SDK objetivo 35; bytecode Java 17.
- Identificador: `cl.androiddlna`; versión `0.1.0` (código 1).
- Android Gradle Plugin 8.9.2; Kotlin 2.1.20; Gradle Wrapper 9.3.0.
- Servidor UPnP/DLNA con ContentDirectory habilitado, accesible desde la misma red y con multicast IPv4 permitido.

Las versiones declaradas en Gradle son la fuente de verdad. No se incluye un servidor multimedia.

## Dependencias

| Dependencia | Versión | Uso |
| --- | --- | --- |
| AndroidX Activity KTX | 1.10.1 | Actividad y navegación Atrás |
| Lifecycle Runtime KTX | 2.9.1 | Corrutinas y ciclo de vida |
| Media3 ExoPlayer | 1.10.0 | Reproducción |
| Media3 UI | 1.10.0 | Controles y selección de pistas |
| Media3 Session | 1.10.0 | Sesión multimedia |
| RecyclerView | 1.4.0 | Galería de tarjetas |
| Coil | 2.7.0 | Carga de carátulas |
| JUnit | 4.13.2 | Pruebas unitarias |

Los artefactos se resuelven desde Google Maven y Maven Central; los plugins también usan Gradle Plugin Portal. SSDP, SOAP y XML se implementan con APIs Android/Java; no se usa una biblioteca DLNA externa.

## Compilación, pruebas e instalación por terminal

```sh
git clone https://github.com/cvargascesp/LumaDLNA_Player_AndroidTV.git
cd LumaDLNA_Player_AndroidTV
```

Configurar `JAVA_HOME` y el SDK mediante Android Studio, `ANDROID_HOME` o un archivo local `local.properties` con `sdk.dir`. Ese archivo no se versiona. La primera compilación necesita Internet para descargar Gradle y dependencias.

Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Linux/macOS:

```sh
sh ./gradlew assembleDebug
sh ./gradlew testDebugUnitTest
sh ./gradlew lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Habilitar depuración en el dispositivo y conectarlo por USB o red para instalar con ADB. `assembleRelease` genera la variante release; no hay firma de distribución configurada. Mantener las claves fuera del repositorio.

## Arquitectura

| Archivo o carpeta | Responsabilidad |
| --- | --- |
| `app/src/main/java/cl/androiddlna/MainActivity.kt` | Interfaz nativa, foco, biblioteca, accesos, caché, progreso y reproductor |
| `app/src/main/java/cl/androiddlna/Dlna.kt` | SSDP, descripción UPnP, Browse SOAP, paginación y metadatos DIDL-Lite |
| `app/src/main/java/cl/androiddlna/ServerIdentity.kt` | Identidad de servidores y migración Plex |
| `app/src/main/java/cl/androiddlna/ShortcutName.kt` | Nombres significativos de accesos |
| `app/src/main/java/cl/androiddlna/SeekGesture.kt` | Avance/retroceso, aceleración y límites |
| `app/src/main/java/cl/androiddlna/LibraryArtwork.kt` | Ilustraciones locales |
| `app/src/main/res/` | Tema, iconos y banner TV |
| `app/src/test/` | Pruebas unitarias |
| `design/` | Fuente SVG y notas visuales |
| `gradle/wrapper/` | Wrapper versionado, incluido su JAR |

Flujo: descubrimiento SSDP → descripción UPnP → navegación ContentDirectory por SOAP → URL de video → Media3. La caché conserva metadatos; no descarga videos para uso sin conexión.

## Datos, red y permisos

Servidores, accesos, listados y progreso se guardan en SharedPreferences privadas (`luma`). Borrar los datos de la aplicación elimina esos registros. El manifiesto deshabilita la copia de seguridad automática.

- `INTERNET`: solicitudes y reproducción desde el servidor.
- `ACCESS_NETWORK_STATE` y `ACCESS_WIFI_STATE`: acceso al estado de conectividad.
- `CHANGE_WIFI_MULTICAST_STATE`: bloqueo multicast durante descubrimiento Wi-Fi.

SSDP usa UDP `239.255.255.250:1900`. Metadatos y videos se obtienen desde las direcciones anunciadas por el servidor. Se permite HTTP sin cifrar para compatibilidad DLNA: utilizar una red de confianza. El proyecto no incluye cuentas, backend propio ni SDK de analítica.

## Solución de problemas

- **No aparecen servidores:** activar DLNA, comprobar conectividad, firewall, VPN y aislamiento de clientes en redes de invitados; permitir multicast.
- **Servidor desconectado visible:** puede proceder de la caché; comprobar disponibilidad y actualizar.
- **Error al abrir carpeta:** revisar la causa HTTP/SOAP en Logcat, etiqueta `LumaDLNA`.
- **Faltan audio o pistas:** la compatibilidad depende de Media3 y decodificadores del dispositivo; probar un archivo compatible.
- **Gradle no encuentra Java o SDK:** revisar JDK de Gradle, `JAVA_HOME` y `local.properties`.
- **Emulador sin descubrimiento:** comprobar acceso al servidor y respuestas SSDP; contrastar con un dispositivo físico.

## Alcance de pruebas y distribución

Las pruebas unitarias cubren identidad/deduplicación de servidores, migración Plex, nombres de accesos y aceleración/límites de búsqueda. No sustituyen pruebas de red, codecs, reproducción o mando físico. Los escenarios pendientes se indican arriba.

El `.gitignore` excluye cachés Gradle/Kotlin, configuración local del IDE/SDK, compilados, APK/AAB, logs y claves de firma. Se incluyen fuentes, recursos, pruebas y Gradle Wrapper.

No se ha definido una licencia de distribución para el proyecto. Las dependencias conservan sus licencias respectivas; revisar sus condiciones antes de distribuir.
