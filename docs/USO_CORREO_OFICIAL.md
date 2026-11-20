# Correo oficial de 25 Lotería Cubana

Dirección oficial de contacto: **25loteriacubana@gmail.com**

## Función incorporada en la APK

- El botón **Soporte** de la barra superior abre una aplicación de correo con el destinatario oficial y el asunto prellenado.
- La pantalla de acceso muestra **Contactar soporte** para que una persona pueda pedir ayuda antes de iniciar sesión.
- La APK no contiene contraseñas del buzón ni credenciales SMTP.

## Funciones de correo y estado

| Función | Estado |
| --- | --- |
| Contactar soporte desde la APK | Incorporada en el código |
| Recibir y responder consultas | Se realiza desde el buzón Gmail |
| Enviar automáticamente códigos de confirmación o recuperación | Pendiente de configurar un proveedor de correo transaccional |
| Notificaciones administrativas automáticas | Pendiente |

## Seguridad y autenticación actual

El registro actual solicita correo y contraseña y muestra al usuario un código único de recuperación una sola vez. La recuperación de contraseña usa ese código; no envía la contraseña actual por correo. El proyecto no debe anunciar que la dirección de correo fue verificada por un mensaje, porque todavía no está activo el envío de confirmaciones.

El correo oficial de soporte es independiente de las credenciales de los usuarios. Nunca se debe pedir ni almacenar la contraseña de Gmail dentro de la APK, GitHub o la base de datos.

## Próximo paso para correo automático

Supabase Auth requiere un servidor SMTP personalizado para enviar mensajes a cualquier dirección de usuario. El SMTP integrado de Supabase es solo para pruebas y restringe los destinatarios a miembros autorizados del proyecto.

Antes de habilitar confirmaciones o recuperación por correo:
1. Elegir un proveedor SMTP compatible con un nivel gratuito adecuado.
2. Configurar las credenciales únicamente en el panel seguro de Supabase.
3. Crear plantillas breves en español para confirmación y recuperación.
4. Probar entrega a una dirección que no sea miembro del equipo.
5. Probar expiración, uso único y límites de reenvío.

No se debe usar la contraseña normal de Gmail como secreto de aplicación. Si más adelante se evalúa Gmail SMTP, habrá que comprobar los requisitos vigentes de Google y usar credenciales específicas de aplicación, guardadas solo en Supabase. No se habilitará el envío automático hasta completar esas pruebas.
