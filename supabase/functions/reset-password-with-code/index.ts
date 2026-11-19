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

async function sha256(value: string): Promise<string> {
  const normalized = value.replace(/[^a-fA-F0-9]/g, "").toLowerCase();
  const data = new TextEncoder().encode(normalized);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Método no permitido." }, 405);

  let userId: string | null = null;
  let claimId: string | null = null;
  let admin: ReturnType<typeof createClient> | null = null;

  try {
    const body = await req.json();
    const email = String(body.email ?? "").trim().toLowerCase();
    const code = String(body.recovery_code ?? "").trim();
    const newPassword = String(body.new_password ?? "");

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254 ||
        !/^(?:[A-Fa-f0-9]{8}-?){4}[A-Fa-f0-9]{8}$/.test(code.replace(/-/g, "")) ||
        newPassword.length < 8 || newPassword.length > 72) {
      return json({ error: "No se pudo recuperar la cuenta. Revisa los datos e inténtalo de nuevo." }, 400);
    }

    const url = Deno.env.get("SUPABASE_URL");
    const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !key) throw new Error("Configuración del servidor incompleta.");

    admin = createClient(url, key, { auth: { autoRefreshToken: false, persistSession: false } });
    claimId = crypto.randomUUID();
    const codeHash = await sha256(code);

    const { data: claimedUser, error: claimError } = await admin.rpc("claim_account_recovery_code", {
      p_email: email,
      p_code_hash: codeHash,
      p_claim_id: claimId,
    });

    if (claimError || !claimedUser) {
      return json({ error: "No se pudo recuperar la cuenta. Revisa el correo y el código, o inténtalo más tarde." }, 400);
    }

    userId = String(claimedUser);
    const { error: updateError } = await admin.auth.admin.updateUserById(userId, {
      password: newPassword,
    });

    if (updateError) {
      await admin.rpc("release_account_recovery_claim", { p_user_id: userId, p_claim_id: claimId });
      userId = null;
      claimId = null;
      return json({ error: "No se pudo actualizar la contraseña. Inténtalo de nuevo." }, 400);
    }

    const { data: completed, error: completeError } = await admin.rpc("complete_account_recovery_code", {
      p_user_id: userId,
      p_claim_id: claimId,
    });

    if (completeError || completed !== true) {
      // La contraseña ya cambió; el reclamo caduca automáticamente en dos minutos.
      return json({ error: "La contraseña se actualizó, pero no se pudo cerrar el código. Contacta con soporte antes de volver a intentarlo." }, 500);
    }

    return json({ success: true, message: "Contraseña actualizada. Ya puedes iniciar sesión en cualquier teléfono." });
  } catch (_error) {
    if (admin && userId && claimId) {
      try {
        await admin.rpc("release_account_recovery_claim", { p_user_id: userId, p_claim_id: claimId });
      } catch (_) { /* No exponer detalles internos. */ }
    }
    return json({ error: "No se pudo procesar la recuperación en este momento." }, 500);
  }
});
