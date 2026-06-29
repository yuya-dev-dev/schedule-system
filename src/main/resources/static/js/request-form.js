document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("request-form");
    if (!form) return;
    if (form.dataset.readOnly === "true") return;

    const workType = document.getElementById("workType");
    const requester = document.getElementById("requesterName");
    const requesterMarker = document.getElementById("requester-required");
    const normalFields = document.getElementById("normal-work-fields");
    const companion = form.querySelector('input[name="companionRequired"]');
    const companionToggle = document.getElementById("companion-toggle");
    const companionFields = document.getElementById("companion-fields");
    const address = document.getElementById("address");
    const addressMarker = document.getElementById("address-required");
    const status = document.getElementById("save-status");
    const statusRow = form.querySelector(".save-status-row");
    const retryButton = document.getElementById("retry-save");
    const errorSummary = document.getElementById("error-summary");
    const idField = document.getElementById("id");
    const versionField = document.getElementById("version");
    const destructiveActions = document.getElementById("destructive-actions");
    const draftDeleteForm = document.getElementById("draft-delete-form");
    const cancelRequestLink = document.getElementById("cancel-request-link");
    const backSubmit = form.querySelector(".back-submit");
    const detailRequiredFields = ["requestDetail", "desiredArrivalTime"]
        .map(id => document.getElementById(id));
    const companionRequiredFields = ["meetingPlace", "departureTime"]
        .map(id => document.getElementById(id));
    const normalFieldIds = [
        "requestDetail", "address", "desiredArrivalTime", "meetingPlace",
        "departureTime", "vehicleName", "note"
    ];
    let previousWorkType = workType.value;
    let saveQueue = Promise.resolve();
    let staleState = false;
    let changeRevision = 0;
    let queuedRevision = -1;

    const isInternalWork = value => value === "RECEIVING" || value === "PRODUCT_MANAGEMENT";
    const isNormalWork = () => workType.value !== "" && !isInternalWork(workType.value);
    const setStatusState = state => {
        statusRow.classList.remove("is-idle", "is-saving", "is-saved", "is-failed");
        statusRow.classList.add(state);
    };

    const clearErrors = () => {
        errorSummary.replaceChildren();
        errorSummary.classList.add("is-hidden");
    };

    const showErrors = messages => {
        errorSummary.replaceChildren(...messages.map(message => {
            const paragraph = document.createElement("p");
            paragraph.textContent = message;
            return paragraph;
        }));
        errorSummary.classList.toggle("is-hidden", messages.length === 0);
    };

    const updateDestructiveActions = (requestId, entryState) => {
        if (!requestId || !entryState) return;
        destructiveActions.hidden = false;
        const draft = entryState === "DRAFT";
        draftDeleteForm.hidden = !draft;
        cancelRequestLink.hidden = draft;
        draftDeleteForm.action = `/requests/drafts/${requestId}/delete`;
        cancelRequestLink.href = `/requests/${requestId}/cancel`;
    };

    const updateBrowserUrl = (requestId, entryState) => {
        if (!requestId || !entryState) return;
        const path = entryState === "DRAFT"
            ? `/requests/drafts/${requestId}`
            : `/requests/${requestId}`;
        if (window.location.pathname !== path) {
            window.history.replaceState(null, "", path);
        }
    };

    const updateDynamicFields = () => {
        const normal = isNormalWork();
        const internal = isInternalWork(workType.value);
        requester.required = !internal;
        requesterMarker.hidden = internal;
        normalFields.hidden = internal;
        companionToggle.hidden = internal;
        document.querySelectorAll(".normal-required").forEach(marker => marker.hidden = !normal);
        detailRequiredFields.forEach(field => field.required = normal);
        address.required = normal && !companion.checked;
        addressMarker.hidden = !normal || companion.checked;
        companionFields.hidden = !companion.checked || internal;
        companionRequiredFields.forEach(field => field.required = normal && companion.checked);
    };

    const clearCompanionValues = () => {
        ["meetingPlace", "departureTime", "vehicleName"].forEach(id => {
            document.getElementById(id).value = "";
        });
    };

    const clearNormalValues = () => {
        normalFieldIds.forEach(id => document.getElementById(id).value = "");
        companion.checked = false;
        document.getElementById("dispatchStatus").value = "UNANSWERED";
    };

    const performAutosave = async () => {
        retryButton.hidden = true;
        retryButton.textContent = "再試行";
        status.textContent = "保存中...";
        status.className = "save-status saving";
        setStatusState("is-saving");
        const response = await fetch(form.dataset.autosaveUrl, {
            method: "POST",
            body: new FormData(form),
            headers: {"X-Requested-With": "XMLHttpRequest"}
        });
        if (!response.ok) throw new Error("保存に失敗しました");
        const result = await response.json();
        const savedOnServer = result.status === "SAVED" || result.status === "TIME_CONFLICT";
        if (savedOnServer && result.requestId !== null) {
            idField.value = result.requestId;
            versionField.value = result.version;
            updateDestructiveActions(result.requestId, result.entryState);
            updateBrowserUrl(result.requestId, result.entryState);
        }

        if (result.status === "SAVED") {
            staleState = false;
            status.textContent = result.entryState === "PUBLISHED" ? "保存済み・一覧に反映中" : "下書き保存済み";
            status.className = "save-status saved";
            setStatusState("is-saved");
            const missing = (result.missingFields || []).map(name => `${name}が未入力です`);
            showErrors(missing);
            return true;
        }
        if (result.status === "TIME_CONFLICT") {
            staleState = false;
            status.textContent = result.entryState === "PUBLISHED"
                ? "時間重複のため変更されませんでした。元の予定を維持しています"
                : "下書き保存済み（時間重複）";
            status.className = "save-status failed";
            setStatusState("is-failed");
            showErrors([result.message]);
            return true;
        }
        if (result.status === "STALE") {
            staleState = true;
            status.textContent = "ほかの利用者が先に変更しました";
            status.className = "save-status failed";
            setStatusState("is-failed");
            retryButton.textContent = "最新内容を読み込む";
            retryButton.hidden = false;
            showErrors([result.message]);
            return false;
        }
        staleState = false;
        status.textContent = result.entryState === "PUBLISHED"
            ? "未入力のため変更されませんでした。元の予定を維持しています"
            : "保存できませんでした";
        status.className = "save-status failed";
        setStatusState("is-failed");
        retryButton.hidden = false;
        const missing = (result.missingFields || []).map(name => `${name}が未入力です`);
        retryButton.hidden = result.entryState === "PUBLISHED" && missing.length > 0;
        showErrors(missing.length > 0 ? missing : [result.message]);
        return false;
    };

    const markDirty = () => {
        changeRevision += 1;
    };

    const autosave = (force = false) => {
        const revision = changeRevision;
        if (!force && queuedRevision === revision) return saveQueue;
        queuedRevision = revision;
        saveQueue = saveQueue.then(performAutosave).catch(() => {
            staleState = false;
            status.textContent = "保存できませんでした。入力欄を変更すると再試行します";
            status.className = "save-status failed";
            setStatusState("is-failed");
            retryButton.hidden = false;
            showErrors(["通信エラーのため保存できませんでした"]);
            return false;
        });
        return saveQueue;
    };

    workType.addEventListener("change", event => {
        if (isInternalWork(event.target.value) && !isInternalWork(previousWorkType)) {
            const hasDetails = normalFieldIds.some(id => document.getElementById(id).value.trim() !== "")
                || companion.checked
                || document.getElementById("dispatchStatus").value !== "UNANSWERED";
            if (hasDetails && !window.confirm("入庫・商品管理へ変更すると、通常案件の詳細入力を消去します。よろしいですか？")) {
                workType.value = previousWorkType;
                updateDynamicFields();
                return;
            }
            clearNormalValues();
        }
        previousWorkType = event.target.value;
        markDirty();
        updateDynamicFields();
        autosave();
    });

    companion.addEventListener("change", () => {
        if (!companion.checked) clearCompanionValues();
        markDirty();
        updateDynamicFields();
        autosave();
    });

    retryButton.addEventListener("click", () => {
        if (staleState) {
            window.location.reload();
            return;
        }
        autosave(true);
    });

    form.querySelectorAll("input:not([type=hidden]):not([type=checkbox]), textarea")
        .forEach(field => {
            field.addEventListener("input", markDirty);
            field.addEventListener("blur", () => autosave());
        });
    form.querySelectorAll("select").forEach(field => {
        if (field !== workType) field.addEventListener("change", () => {
            markDirty();
            autosave();
        });
    });

    const returnToSchedule = async () => {
        clearErrors();
        if (changeRevision === 0) {
            window.location.assign(form.dataset.scheduleUrl);
            return;
        }
        const saved = await autosave();
        if (saved) window.location.assign(form.dataset.scheduleUrl);
    };

    form.addEventListener("submit", async event => {
        event.preventDefault();
        await returnToSchedule();
    });

    backSubmit.addEventListener("click", async () => {
        await returnToSchedule();
    });

    updateDynamicFields();
    setStatusState("is-idle");
});
