package ng.asuu.thrift.web.dto;

/** amount is only used by loan approval - lets an admin grant a different amount than what was
 *  requested (a real scenario in the cooperative); null/absent leaves the requested amount as-is. */
public record DecisionRequest(String note, Long amount) {
}
