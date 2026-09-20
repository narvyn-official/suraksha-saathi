export type Role = "admin" | "trainer" | "viewer" | "certifier";
export type Permission = "read" | "write" | "admin" | "certify" | "revoke";
export function permitted(role: Role, permission: Permission) {
  if (!["admin", "trainer", "viewer", "certifier"].includes(role)) return false;
  if(permission==='read')return true;
  if(permission==='certify')return role==='certifier';
  if(permission==='revoke')return role==='certifier'||role==='admin';
  if(permission==='admin')return role==='admin';
  return permission==='write'&&(role==='admin'||role==='trainer');
}
export function assignmentStatus(row: {cancelled_at: number | null; due_at: number; completed_at: number | null}, now: number) {
  return row.cancelled_at !== null ? "cancelled" : row.completed_at !== null ? "complete" : row.due_at < now ? "overdue" : "assigned";
}
