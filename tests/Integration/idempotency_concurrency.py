import json
import threading
import urllib.error
import urllib.request
import uuid

BASES = (
    "http://127.0.0.1:8000/api/v1",
    "http://127.0.0.1:8001/api/v1",
)
ROUNDS = 30


def request(base, method, path, payload=None, token=None):
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(
        base + path,
        data=None if payload is None else json.dumps(payload).encode(),
        headers=headers,
        method=method,
    )

    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        body = json.loads(error.read().decode())
        return error.code, body


def create_account(token, suffix):
    status, body = request(
        BASES[0],
        "POST",
        "/accounts",
        {
            "name": f"Concurrency {suffix}",
            "balance": 100_000,
            "currency": "DOP",
            "country_code": "DO",
        },
        token,
    )
    assert status == 201, body
    return body["data"]["id"]


def main():
    email = f"concurrency-{uuid.uuid4().hex[:12]}@example.com"
    status, body = request(
        BASES[0],
        "POST",
        "/auth/register",
        {
            "name": "Concurrency Test",
            "email": email,
            "password": "Password123!",
            "password_confirmation": "Password123!",
            "device_name": "concurrency-test",
        },
    )
    assert status == 201, body
    token = body["data"]["token"]

    failures = []
    canonical_account = create_account(token, "canonical")
    canonical_key = str(uuid.uuid4())
    canonical_payload = {
        "account_id": canonical_account,
        "amount": "-100",
        "currency": "DOP",
        "description": "Canonical operation",
        "timestamp": "2026-07-20T12:30:00.123-04:00",
        "status": "completed",
        "idempotency_key": canonical_key,
    }
    first_status, first_body = request(
        BASES[0], "POST", "/transactions", canonical_payload, token
    )
    retry_status, retry_body = request(
        BASES[1], "POST", "/transactions", canonical_payload, token
    )
    equivalent_status, equivalent_body = request(
        BASES[0],
        "POST",
        "/transactions",
        {
            **canonical_payload,
            "amount": -100,
            "timestamp": "2026-07-20T16:30:00Z",
        },
        token,
    )
    canonical_ids = {
        first_body.get("data", {}).get("id"),
        retry_body.get("data", {}).get("id"),
        equivalent_body.get("data", {}).get("id"),
    }
    canonicalization = {
        "statuses": [first_status, retry_status, equivalent_status],
        "same_transaction": len(canonical_ids) == 1 and None not in canonical_ids,
    }
    if canonicalization != {"statuses": [201, 200, 200], "same_transaction": True}:
        failures.append({"canonicalization": canonicalization})

    for round_number in range(ROUNDS):
        accounts = [
            create_account(token, f"{round_number}-a"),
            create_account(token, f"{round_number}-b"),
        ]
        key = str(uuid.uuid4())
        barrier = threading.Barrier(2)
        results = [None, None]

        def send(index):
            barrier.wait()
            results[index] = request(
                BASES[index],
                "POST",
                "/transactions",
                {
                    "account_id": accounts[index],
                    "amount": -100,
                    "currency": "DOP",
                    "description": "Concurrent operation",
                    "timestamp": "2026-07-20T16:30:00Z",
                    "status": "completed",
                    "idempotency_key": key,
                },
                token,
            )[0]

        threads = [threading.Thread(target=send, args=(index,)) for index in range(2)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()

        statuses = sorted(results)
        if statuses != [201, 409]:
            failures.append({"round": round_number + 1, "statuses": statuses})

    summary = {
        "canonicalization": canonicalization,
        "concurrency_rounds": ROUNDS,
        "failures": failures,
        "passed": not failures,
    }
    print(json.dumps(summary, indent=2))
    raise SystemExit(0 if not failures else 1)


if __name__ == "__main__":
    main()
