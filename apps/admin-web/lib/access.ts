export type Role = "admin" | "trainer" | "viewer";
export function permitted(role: Role, permission: "read" | "write" | "admin") {
  if (!["admin", "trainer", "viewer"].includes(role)) return false;
  return permission === "read" || role === "admin" || (permission === "write" && role === "trainer");
}
export function assignmentStatus(row: {cancelled_at: number | null; due_at: number; completed_at: number | null}, now: number) {
  return row.cancelled_at !== null ? "cancelled" : row.completed_at !== null ? "complete" : row.due_at < now ? "overdue" : "assigned";
}
