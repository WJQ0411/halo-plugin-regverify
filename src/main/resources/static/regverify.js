(function () {
  "use strict";

  var cfg = window.__REGVERIFY__;
  if (!cfg || !cfg.texts) {
    return;
  }
  var texts = cfg.texts;
  var form = document.getElementById("signup-form");
  if (!form) {
    return;
  }

  var verifiedAnswer = "";
  var modal = null;
  var input = null;
  var errorNode = null;
  var submitButton = null;
  var statusNode = null;
  var verifyButton = null;

  function node(tag, className, text) {
    var element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    if (text !== undefined && text !== null) {
      element.textContent = text;
    }
    return element;
  }

  function setStatus(state, text) {
    if (!statusNode) {
      return;
    }
    statusNode.setAttribute("data-state", state);
    statusNode.textContent = text;
  }

  function setBusy(busy) {
    if (verifyButton) {
      verifyButton.disabled = busy;
      verifyButton.textContent = busy ? texts.verifying : texts.submit;
    }
    if (input) {
      input.disabled = busy;
    }
    if (submitButton) {
      submitButton.disabled = busy;
    }
  }

  function showError(text) {
    if (!errorNode) {
      return;
    }
    if (text) {
      errorNode.textContent = text;
      errorNode.hidden = false;
    } else {
      errorNode.textContent = "";
      errorNode.hidden = true;
    }
  }

  function openModal(message) {
    if (!modal) {
      return;
    }
    showError(message || "");
    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("regverify-locked");
    if (input) {
      if (verifiedAnswer) {
        input.value = verifiedAnswer;
      }
      window.setTimeout(function () {
        input.focus();
      }, 0);
    }
  }

  function closeModal() {
    if (!modal) {
      return;
    }
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("regverify-locked");
    var openButton = document.getElementById("regverify-open");
    if (openButton) {
      openButton.focus();
    }
  }

  function collect(field) {
    var element = form.querySelector('[name="' + field + '"]');
    return element && typeof element.value === "string" ? element.value : "";
  }

  function requestVerification() {
    var answer = input ? input.value.trim() : "";
    if (!answer) {
      showError(texts.empty);
      return;
    }
    setBusy(true);
    showError("");
    fetch(cfg.apiPath, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify({
        answer: answer,
        username: collect("username"),
        email: collect("email"),
        displayName: collect("displayName")
      })
    })
      .then(function (response) {
        if (response.status === 429) {
          throw new Error(texts.rateLimited);
        }
        return response.json().catch(function () {
          return {};
        }).then(function (body) {
          if (!response.ok && !body.message) {
            throw new Error(texts.fail);
          }
          return body;
        });
      })
      .then(function (body) {
        if (body && body.valid) {
          verifiedAnswer = answer;
          var hidden = form.querySelector('[name="regverifyAnswer"]');
          if (hidden) {
            hidden.value = answer;
          }
          setStatus("ok", texts.success);
          closeModal();
          return;
        }
        showError((body && body.message) || texts.fail);
      })
      .catch(function (error) {
        showError((error && error.message) || texts.fail);
      })
      .then(function () {
        setBusy(false);
      });
  }

  function buildModal() {
    modal = node("div", "regverify-overlay");
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");

    var dialog = node("div", "regverify-modal");
    var title = node("h2", "regverify-modal-title", texts.title);
    title.id = "regverify-modal-title";
    modal.setAttribute("aria-labelledby", title.id);

    var description = node("p", "regverify-modal-description", texts.description);

    var label = node("label", "regverify-modal-label", texts.inputLabel);
    label.setAttribute("for", "regverify-input");

    input = node("input", "regverify-modal-input");
    input.id = "regverify-input";
    input.type = "text";
    input.autocomplete = "off";
    input.spellcheck = false;
    input.placeholder = texts.inputPlaceholder || "";
    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        event.preventDefault();
        requestVerification();
      }
    });

    errorNode = node("p", "regverify-modal-error");
    errorNode.hidden = true;
    errorNode.setAttribute("role", "alert");

    var actions = node("div", "regverify-modal-actions");
    var cancelButton = node("button", "regverify-btn regverify-btn-secondary", texts.cancel);
    cancelButton.type = "button";
    cancelButton.addEventListener("click", closeModal);

    verifyButton = node("button", "regverify-btn regverify-btn-primary", texts.submit);
    verifyButton.type = "button";
    verifyButton.addEventListener("click", requestVerification);

    actions.appendChild(cancelButton);
    actions.appendChild(verifyButton);

    dialog.appendChild(title);
    dialog.appendChild(description);
    dialog.appendChild(label);
    dialog.appendChild(input);
    dialog.appendChild(errorNode);
    dialog.appendChild(actions);
    modal.appendChild(dialog);

    modal.addEventListener("click", function (event) {
      if (event.target === modal) {
        closeModal();
      }
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && modal && !modal.hidden) {
        closeModal();
      }
    });

    var host = document.querySelector(".gateway-wrapper") || document.body;
    host.appendChild(modal);
  }

  function buildEntry() {
    var block = node("div", "form-item regverify-field");

    var label = node("label", "regverify-field-label", texts.title);
    label.setAttribute("for", "regverify-open");

    var control = node("div", "regverify-field-control");
    var openButton = node("button", "regverify-btn regverify-btn-secondary", texts.button);
    openButton.type = "button";
    openButton.id = "regverify-open";
    openButton.addEventListener("click", function () {
      openModal("");
    });
    control.appendChild(openButton);

    statusNode = node("p", "regverify-status");
    setStatus("pending", texts.unverified);

    var hidden = node("input", "regverify-answer");
    hidden.type = "hidden";
    hidden.name = "regverifyAnswer";
    hidden.value = "";

    block.appendChild(label);
    block.appendChild(control);
    block.appendChild(statusNode);
    block.appendChild(hidden);

    var submit = form.querySelector('button[type="submit"]');
    var anchor = submit ? submit.closest(".form-item") : null;
    if (anchor && anchor.parentNode) {
      anchor.parentNode.insertBefore(block, anchor);
    } else if (submit) {
      submit.parentNode.insertBefore(block, submit);
    } else {
      form.appendChild(block);
    }
    return hidden;
  }

  function init() {
    buildModal();
    var hidden = buildEntry();
    submitButton = form.querySelector('button[type="submit"]');

    if (typeof cfg.preVerified === "string" && cfg.preVerified) {
      verifiedAnswer = cfg.preVerified;
      hidden.value = cfg.preVerified;
      setStatus("ok", texts.success);
    }

    form.addEventListener("submit", function (event) {
      if (verifiedAnswer) {
        return;
      }
      event.preventDefault();
      openModal(texts.fail);
    });

    if (window.location.search.indexOf("regverify=failed") !== -1) {
      verifiedAnswer = "";
      hidden.value = "";
      setStatus("error", texts.fail);
      openModal(texts.fail);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
