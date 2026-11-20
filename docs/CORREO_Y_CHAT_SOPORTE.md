# Correo oficial y chat de soporte

Correo público de contacto: **25loteriacubana@gmail.com**.

## Chat dentro de la APK
- Los usuarios pueden abrir conversaciones privadas y enviar mensajes desde la aplicación.
- La app Admin tiene una bandeja separada para responder.
- La base de datos aplica RLS: cada usuario solo puede leer sus conversaciones; solo los usuarios registrados en `support_admins` pueden ver todas y responder como soporte.
- Los mensajes son texto plano con límite de 2.000 caracteres. No se admiten contraseñas, códigos de recuperación ni datos bancarios.
- La APK mantiene correo externo como alternativa de contacto.
- El chat actualiza manualmente al abrir o tocar Actualizar; todavía no usa notificaciones push ni realtime.

## Activar la cuenta administradora
No se concede privilegio administrativo automáticamente a ninguna cuenta existente. Tras confirmar qué cuenta debe administrar el soporte, añadir su UUID de Auth a `public.support_admins` desde el panel SQL seguro de Supabase. Nunca añadir un usuario final como administrador por defecto.

## Correo saliente de Supabase Auth
El código de registro actual crea cuentas con `email_confirm: true` porque todavía no se ha habilitado el envío de confirmación. Eso no demuestra que el usuario controle el buzón. No anunciar el correo del usuario como verificado.

Para activar confirmación y recuperación por email:
1. En Gmail, habilitar la verificación en dos pasos.
2. Crear una contraseña de aplicación específica para Supabase (si Google ofrece esa opción para la cuenta).
3. En Supabase Dashboard, abrir Authentication → SMTP Settings y habilitar Custom SMTP.
4. Configurar host `smtp.gmail.com`, puerto `465` (SSL) o `587` (STARTTLS), usuario y remitente `25loteriacubana@gmail.com`; usar la contraseña de aplicación, nunca la contraseña normal de Gmail.
5. Guardar el secreto solo en el panel de Supabase. No compartirlo en el chat, GitHub ni la APK.
6. Configurar plantillas de confirmación y recuperación en español y probar con una dirección externa.

Antes de activar confirmación obligatoria, hay que adaptar el flujo de registro para que el usuario confirme el correo antes del primer inicio de sesión. Eso modifica el flujo de alta automática actual y debe probarse junto con el código único de recuperación. No cambiar `email_confirm` a false sin completar primero ese trabajo.

El envío automático no se considera activo hasta que Supabase acepte el SMTP y se confirme la entrega real.
