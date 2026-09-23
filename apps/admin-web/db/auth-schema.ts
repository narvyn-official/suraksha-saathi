import { sqliteTable, text, integer, index } from "drizzle-orm/sqlite-core";
const timestamp = (name: string) => integer(name, {mode: "timestamp_ms"}).notNull();
export const user = sqliteTable("auth_user", {
 id:text("id").primaryKey(), name:text("name").notNull(), email:text("email").notNull().unique(), emailVerified:integer("email_verified",{mode:"boolean"}).notNull().default(false), twoFactorEnabled:integer("two_factor_enabled",{mode:"boolean"}).notNull().default(false), image:text("image"), createdAt:timestamp("created_at"), updatedAt:timestamp("updated_at"),
});
export const session = sqliteTable("auth_session", {
 id:text("id").primaryKey(), userId:text("user_id").notNull().references(()=>user.id,{onDelete:"cascade"}), token:text("token").notNull().unique(), expiresAt:timestamp("expires_at"), ipAddress:text("ip_address"), userAgent:text("user_agent"), createdAt:timestamp("created_at"), updatedAt:timestamp("updated_at"),
},t=>[index("auth_session_user").on(t.userId)]);
export const account = sqliteTable("auth_account", {
 id:text("id").primaryKey(), userId:text("user_id").notNull().references(()=>user.id,{onDelete:"cascade"}), accountId:text("account_id").notNull(), providerId:text("provider_id").notNull(), accessToken:text("access_token"), refreshToken:text("refresh_token"), idToken:text("id_token"), scope:text("scope"), password:text("password"), accessTokenExpiresAt:integer("access_token_expires_at",{mode:"timestamp_ms"}), refreshTokenExpiresAt:integer("refresh_token_expires_at",{mode:"timestamp_ms"}), createdAt:timestamp("created_at"), updatedAt:timestamp("updated_at"),
},t=>[index("auth_account_user").on(t.userId)]);
export const verification = sqliteTable("auth_verification", {id:text("id").primaryKey(),identifier:text("identifier").notNull(),value:text("value").notNull(),expiresAt:timestamp("expires_at"),createdAt:timestamp("created_at"),updatedAt:timestamp("updated_at")},t=>[index("auth_verification_identifier").on(t.identifier)]);
export const rateLimit = sqliteTable("auth_rate_limit", {id:text("id").primaryKey(),key:text("key").notNull().unique(),count:integer("count").notNull(),lastRequest:integer("last_request").notNull()});

export const twoFactor = sqliteTable("auth_two_factor",{id:text("id").primaryKey(),secret:text("secret").notNull(),backupCodes:text("backup_codes").notNull(),userId:text("user_id").notNull().references(()=>user.id,{onDelete:"cascade"}),verified:integer("verified",{mode:"boolean"}).default(true),failedVerificationCount:integer("failed_verification_count").notNull().default(0),lockedUntil:integer("locked_until",{mode:"timestamp_ms"})});
