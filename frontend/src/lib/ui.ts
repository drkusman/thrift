export function formatNaira(n: number | null | undefined) {
  if (n === null || n === undefined) return "-";
  return "₦" + n.toLocaleString("en-NG");
}

export function statusBadgeClass(status: string) {
  switch (status) {
    case "APPROVED":
    case "DISBURSED":
    case "RUNNING":
    case "COMPLETED":
    case "ACTIVE":
    case "ACCEPTED":
      return "badge badge-green";
    case "REJECTED":
    case "DEFAULTED":
    case "WITHDRAWN":
    case "DECEASED":
      return "badge badge-red";
    case "PENDING":
      return "badge badge-gold";
    case "PULSED":
      return "badge"; // base maroon styling - a distinct fourth color from gold/green/red/grey
    default:
      return "badge badge-grey";
  }
}
