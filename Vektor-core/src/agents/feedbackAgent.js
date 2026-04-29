export const feedbackAgent = ({ offlineDecision, onlineDecision }) => {
    if (!offlineDecision) {
        return { override_required: false, reason: "no_offline_decision" };
    }

    const offlineHospital = offlineDecision?.hospital?.hospital_id;
    const onlineHospital = onlineDecision?.hospital?.hospital_id;
    const sameHospital = offlineHospital && onlineHospital && offlineHospital === onlineHospital;

    const overrideRequired = !sameHospital || offlineDecision?.decision?.priority !== onlineDecision?.decision?.priority;

    return {
        override_required: overrideRequired,
        reason: overrideRequired ? "cloud_decision_differs" : "decisions_match",
        offline_summary: {
            hospital_id: offlineHospital,
            priority: offlineDecision?.decision?.priority,
        },
        online_summary: {
            hospital_id: onlineHospital,
            priority: onlineDecision?.decision?.priority,
        },
    };
};
