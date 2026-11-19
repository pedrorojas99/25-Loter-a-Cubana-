import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status, headers: { ...cors, "Content-Type": "application/json" },
  });
}

function makeRecoveryCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(20));
  const raw = Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("").toUpperCase();
  return raw.match(/.{1,8}/g)!.join("-");
}

async function sha256(value: string): Promise<string> {
  const data = new TextEncoder().encode(value.replace(/-/g, "").toLowerCase());
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Método no permitido." }, 405);

  let createdUserId: string | null = null;
  try {
    const body = await req.json();
    const username = String(body.username ?? "").trim();
    const email = String(body.email ?? "").trim().toLowerCase();
    const password = String(body.password ?? "");

    if (!/^[A-Za-z0-9_.]{3,20}$/.test(username)) {
      return json({ error: "El nombre de usuario debe tener entre 3 y 20 caracteres." }, 400);
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
      return json({ error: "Escribe un correo electrónico válido." }, 400);
    }
    if (password.length < 8 || password.length > 72) {
      return json({ error: "La contraseña debe tener entre 8 y 72 caracteres." }, 400);
    }

    const url = Deno.env.get("SUPABASE_URL");
    const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !key) throw new Error("Configuración del servidor incompleta.");

    const admin = createClient(url, key, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const { data, error } = await admin.auth.admin.createUser({
      email,
      password,
      // Se omite la confirmación por correo por decisión del producto.
      // El correo es identificador de acceso, no una dirección verificada.
      email_confirm: true,
      user_metadata: { username, display_name: username },
    });

    if (error || !data.user) {
      const msg = (error?.message ?? "").toLowerCase();
      const duplicate = msg.includes("already") || msg.includes("duplicate") ||
        msg.includes("unique") || msg.includes("username");
      return json({
        error: duplicate
          ? "Ese correo o nombre de usuario ya está registrado."
          : "No se pudo crear la cuenta. Comprueba los datos e inténtalo de nuevo.",
      }, duplicate ? 409 : 400);
    }

    createdUserId = data.user.id;
    const recoveryCode = makeRecoveryCode();
    const codeHash = await sha256(recoveryCode);
    const { error: saveError } = await admin.from("account_recovery_codes").insert({
      user_id: createdUserId,
      code_hash: codeHash,
    });

    if (saveError) {
      await admin.auth.admin.deleteUser(createdUserId);
      createdUserId = null;
      throw new Error("No se pudo preparar la recuperación segura de la cuenta.");
    }

    // El código en texto claro se devuelve una sola vez y nunca se registra en logs.
    return json({
      success: true,
      username,
      recovery_code: recoveryCode,
      message: "Cuenta creada. Guarda tu código único de recuperación; no volverá a mostrarse.",
    }, 201);
  } catch (_error) {
    if (createdUserId) {
      try {
        const url = Deno.env.get("SUPABASE_URL");
        const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
        if (url && key) {
          const admin = createClient(url, key, { auth: { autoRefreshToken: false, persistSession: false } });
          await admin.auth.admin.deleteUser(createdUserId);
        }
      } catch (_) { /* No exponer detalles internos. */ }
    }
    return json({ error: "No se pudo procesar el registro en este momento." }, 500);
  }
});
