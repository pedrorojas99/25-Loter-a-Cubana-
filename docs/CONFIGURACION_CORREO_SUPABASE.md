# Configurar Gmail para 25 Lotería Cubana

La recuperación de contraseña utiliza el OTP nativo de Supabase Auth: un código temporal de **6 cifras** enviado por correo. No se utiliza SMS ni un código personal permanente.

## Antes de activar la recuperación por correo

El SMTP predeterminado de Supabase no es un servicio de entrega general: en proyectos alojados limita los destinatarios y la frecuencia. Para enviar a cualquier usuario se debe configurar SMTP personalizado. En este proyecto se utilizará la cuenta oficial:

- Remitente: `25loteriacubana@gmail.com`
- Servidor SMTP de Gmail: `smtp.gmail.com`
- Puerto: `465` con SSL/TLS, o `587` con STARTTLS
- Usuario SMTP: la dirección completa de Gmail
- Contraseña SMTP: una **contraseña de aplicación de Google**, nunca la contraseña normal de Gmail

Google exige activar la verificación en dos pasos para crear una contraseña de aplicación. Si Google no ofrece esa opción para la cuenta, no se podrá completar este método con Gmail SMTP.

## Configuración segura en Supabase

1. Abre [SMTP Settings de este proyecto](https://supabase.com/dashboard/project/pnnujsvixsglydhafzih/auth/smtp).
2. Activa **Enable Custom SMTP**.
3. Introduce el host `smtp.gmail.com`, puerto `465` (SSL/TLS), usuario `25loteriacubana@gmail.com`, la contraseña de aplicación y el remitente verificado.
4. Guarda la configuración. Los secretos se introducen únicamente en el panel de Supabase; nunca los envíes por el chat ni los guardes en GitHub o dentro de la APK.
5. En **Authentication → Email Templates → Reset Password**, configura el mensaje para mostrar el OTP con `{{ .Token }}`. No uses solamente `{{ .ConfirmationURL }}`, porque la APK solicita que el usuario escriba el código.
6. En **Authentication → Rate Limits**, conserva límites contra reenvíos abusivos.
7. En la configuración de Auth, fija la longitud de OTP de correo en **6** y su vencimiento en **600 segundos (10 minutos)**, si esos campos aparecen en el panel. La interfaz de la APK espera exactamente seis cifras.

## Cómo funciona en la APK

1. El usuario toca **Olvidé mi contraseña** e introduce el correo.
2. Supabase envía un OTP al buzón correspondiente.
3. El usuario introduce las seis cifras y una contraseña nueva.
4. La APK verifica el OTP con tipo `recovery` y actualiza la contraseña usando la sesión temporal emitida por Supabase.
5. El OTP es de un solo uso y expira según la configuración de Auth.

El correo no debe confundirse con una dirección verificada al registrarse: el registro actual crea la cuenta sin confirmación de correo. La recuperación sí exige acceso al buzón en ese momento. Más adelante conviene añadir confirmación de correo al registro antes de abrir la aplicación al público.

## Pruebas obligatorias antes de retirar el mecanismo anterior

- Configurar SMTP y confirmar que el correo llega a una dirección externa al equipo del proyecto.
- Solicitar recuperación desde la APK y recibir un código de seis cifras.
- Verificar que un código incorrecto y uno vencido son rechazados.
- Confirmar que el mismo código no se puede reutilizar.
- Cambiar la contraseña y entrar con la nueva desde otro teléfono.
- Revisar que la cuenta, las jugadas y el historial continúan asociados al mismo usuario.

**Importante:** hasta completar estas pruebas, se conserva temporalmente el mecanismo anterior en el servidor para no bloquear cuentas existentes. No se ha eliminado ninguna tabla ni función de recuperación antigua todavía.
