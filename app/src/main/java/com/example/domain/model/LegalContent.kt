package com.example.domain.model

object LegalContent {
    const val TERMS_VERSION = "1.0"

    const val OFFICIAL_TERMS_TITLE = "TÉRMINOS Y CONDICIONES DE MURMUR"

    const val OFFICIAL_TERMS_BODY = """
1. RESPONSABILIDAD DEL CONTENIDO

Cada usuario es responsable del contenido que publica en MURMUR. Las opiniones, mensajes y publicaciones corresponden a sus respectivos autores.

2. VERACIDAD DE LAS PUBLICACIONES

El contenido publicado por los usuarios no necesariamente ha sido verificado. Los lectores deben evitar asumir que todas las publicaciones representan hechos comprobados.

3. RESPETO ENTRE USUARIOS

Está prohibido utilizar MURMUR para acosar, amenazar, humillar, difamar o perjudicar a otras personas. El anonimato no autoriza conductas abusivas.

4. PRIVACIDAD Y DIGNIDAD

Se prohíbe divulgar información privada, imágenes íntimas o datos personales de terceros sin autorización, así como atentar contra su dignidad, honor, imagen o derechos.

5. USO DE LA PLATAFORMA

MURMUR está dirigido a su público autorizado conforme a los requisitos de edad. Cada usuario debe utilizar la aplicación de manera responsable y respetar las normas establecidas.

6. ACEPTACIÓN

Al registrarse y utilizar MURMUR, el usuario declara haber leído y aceptado estos términos y la política de privacidad.
    """

    const val PRIVACY_POLICY_TITLE = "POLÍTICA DE PRIVACIDAD DE MURMUR"

    const val PRIVACY_POLICY_BODY = """
1. INFORMACIÓN QUE RECOPILAMOS

Recopilamos los datos necesarios para operar tu cuenta de manera segura: identificador único de cuenta (UID), correo electrónico proporcionado por el proveedor de autenticación, nombre de usuario, nombre visible opcional, biografía, fotografía de perfil opcional, confirmación de edad, registro de aceptación de términos y preferencias de la aplicación.

2. PARA QUÉ UTILIZAMOS LA INFORMACIÓN

Utilizamos tus datos para autenticar tu sesión, evitar suplantaciones y nombres duplicados, mostrar tu perfil social, gestionar relaciones de seguimiento, habilitar la mensajería privada entre participantes autorizados, procesar denuncias de seguridad y enviarte avisos configurables.

3. ALMACENAMIENTO Y PROTECCIÓN DE CUENTAS

Tus datos se almacenan en infraestructura de Google Firebase (Firebase Authentication y Cloud Firestore) con reglas de acceso basadas en autenticación y aislamiento por usuario. Las credenciales de acceso se gestionan directamente mediante los proveedores de autenticación de Firebase; MURMUR nunca almacena contraseñas en texto plano en la base de datos.

4. INFORMACIÓN VISIBLE PÚBLICAMENTE

En tu perfil social son visibles tu nombre de usuario, nombre visible opcional, biografía, fotografía de perfil y contadores de seguidores y seguidos. Tu correo electrónico, confirmación de edad, publicaciones guardadas, bloqueos y configuraciones privadas permanecen aislados únicamente para tu cuenta.

5. CÓMO FUNCIONA EL ANONIMATO

Todas las publicaciones del feed son anónimas por diseño. El identificador privado del autor se almacena en una colección protegida separada (`postAuthors`) accesible exclusivamente para la propia cuenta autora, y nunca se incluye ni envía en las publicaciones públicas (`/posts`). Ningún otro usuario puede consultar desde la aplicación ni desde la API quién creó una publicación anónima. Ten en cuenta que, aunque protegemos la separación técnica en la plataforma, ningún sistema digital ofrece garantías absolutas frente a información que tú mismo decidas revelar dentro del texto o contenido multimedia.

6. FOTOGRAFÍAS Y VÍDEOS

Las imágenes y vídeos se seleccionan mediante los selectores nativos de Android con permisos mínimos, se validan por formato, duración y tamaño, y se comprimen antes de procesarse o almacenarse. Al eliminar una publicación propia o tu cuenta, se eliminan las referencias asociadas.

7. PROTECCIÓN DE MENORES Y CONTROLES DE EDAD

MURMUR está diseñado considerando un público adolescente y requiere confirmación de edad mínima conforme a la normativa aplicable (13+ años o edad mínima local). Incluimos filtros preventivos contra exposición de datos personales, bloqueo inmediato de usuarios y herramientas de denuncia de acoso, amenazas o contenido íntimo no consentido.

8. ELIMINACIÓN DE CUENTA

Puedes eliminar tu cuenta desde Configuración > Cuenta > Eliminar cuenta. Al hacerlo, se elimina tu perfil público, tu información privada, tu reserva de nombre de usuario y tu registro de cuenta autenticada.

9. PROVEEDORES EXTERNOS

MURMUR utiliza servicios de Google Firebase (Authentication, Cloud Firestore y servicios asociados configurados en el proyecto) para autenticación, almacenamiento y sincronización en tiempo real, sujetos a las políticas de tratamiento de datos y seguridad de Google Cloud / Firebase.
    """
}
