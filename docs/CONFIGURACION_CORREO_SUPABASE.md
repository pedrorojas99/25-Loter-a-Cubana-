# Configurar correo de 25 Lotería Cubana

La APK implementa registro con correo, verificación por código, inicio de sesión y recuperación de contraseña mediante OTP de Supabase Auth.

## Requisito antes de abrir el registro al público

El SMTP predeterminado de Supabase no es para producción: solo entrega correos a direcciones autorizadas del equipo y tiene límites muy bajos. Para enviar códigos a cualquier usuario hay que conectar un proveedor SMTP externo. Elegir un plan gratuito que cubra el volumen esperado; no activar planes de pago.

## Configuración en Supabase

1. Abre el proyecto `pnnujsvixsglydhafzih` en Supabase Dashboard.
2. Ve a **Project Settings / Authentication / SMTP Settings** (la ubicación exacta puede variar).
3. Crea una cuenta en un proveedor SMTP compatible y copia host, puerto, usuario, contraseña SMTP y dirección remitente verificada.
4. Activa **Custom SMTP** y guarda esos datos en el panel seguro de Supabase. No los guardes en GitHub, APK ni código fuente.
5. En **Authentication / Email Templates**, configura:
   - **Confirm signup**: asunto `Código para activar tu cuenta · 25 Lotería Cubana`; pega el contenido de `supabase/templates/confirmation.html`.
   - **Reset password**: asunto `Código para recuperar tu cuenta · 25 Lotería Cubana`; pega el contenido de `supabase/templates/recovery.html`.
6. Comprueba que ambos mensajes muestran `{{ .Token }}` (OTP), no solo `{{ .ConfirmationURL }}`.
7. Mantén activada la confirmación de correo y limita la frecuencia de reenvío para evitar abuso.

## Pruebas obligatorias

- Registrar una cuenta con un correo real que no pertenezca al equipo del proyecto.
- Confirmar que llega el código y que la app acepta el OTP de registro.
- Iniciar sesión con ese correo y contraseña.
- Solicitar recuperación, confirmar que llega un OTP distinto y cambiar la contraseña.
- Cerrar sesión e iniciar en otro teléfono con la misma cuenta.
- Comprobar que los datos de cuenta consultados desde la nube siguen asociados al mismo usuario.

No publicar la APK para usuarios reales hasta que estas pruebas pasen. El repositorio no contiene contraseñas SMTP ni claves privadas.
