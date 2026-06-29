#!/usr/bin/env python3
"""Render enterprise-style architecture diagram PNGs (Exol/reference visual style)."""

from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle, Ellipse, Rectangle, Arc

DIAGRAMS_DIR = Path(__file__).resolve().parent.parent / "docs" / "diagrams"
ASSETS_DIR = Path(__file__).resolve().parent.parent / "docs" / "assets"

# Reference palette (dark enterprise panels + GCP accents)
C_HEADER = "#2F3B4D"
C_PANEL = "#4A6278"
C_PANEL_EDGE = "#6B8499"
C_PANEL_TITLE = "#FFFFFF"
C_PANEL_SUB = "#C5D3DE"
C_INNER = "#5C7389"
C_INNER_LIGHT = "#E8EEF3"
C_TEXT = "#1F2933"
C_TEXT_LIGHT = "#F7FAFC"
C_MUTED = "#8FA4B8"
C_ARROW = "#D1DCE6"
C_BQ_TOP = "#F9AB00"
C_BQ_MID = "#F29900"
C_BQ_BOT = "#E37400"
C_ACCENT = "#4285F4"
C_VERTEX = "#A142F4"
C_SPRING = "#6DB33F"
C_GDA = "#EA4335"
C_DASH = "#90CAF9"


def _save(fig, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output, dpi=200, bbox_inches="tight", facecolor="white", pad_inches=0.35)
    plt.close(fig)


def _header(ax, title: str, x0: float, y0: float, w: float, h: float = 0.55) -> None:
    ax.add_patch(FancyBboxPatch(
        (x0, y0), w, h, boxstyle="square,pad=0", facecolor=C_HEADER, edgecolor="none", zorder=5))
    ax.text(x0 + w / 2, y0 + h / 2, title, ha="center", va="center",
            fontsize=13, weight="bold", color=C_PANEL_TITLE, zorder=6)


def _panel(ax, x: float, y: float, w: float, h: float, title: str, subtitle: str) -> None:
    ax.add_patch(FancyBboxPatch(
        (x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.06",
        facecolor=C_PANEL, edgecolor=C_PANEL_EDGE, linewidth=1.5, zorder=1))
    ax.text(x + w / 2, y + h - 0.38, title, ha="center", va="center",
            fontsize=11, weight="bold", color=C_PANEL_TITLE, zorder=3)
    ax.text(x + w / 2, y + h - 0.72, subtitle, ha="center", va="center",
            fontsize=8, color=C_PANEL_SUB, zorder=3)


def _inner_box(ax, x, y, w, h, title, lines=None, face=C_INNER, edge="#FFFFFF", fs=8):
    ax.add_patch(FancyBboxPatch(
        (x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.05",
        facecolor=face, edgecolor=edge, linewidth=1.2, alpha=0.95, zorder=4))
    ax.text(x + w / 2, y + h - 0.28, title, ha="center", va="top",
            fontsize=fs, weight="bold", color=C_TEXT_LIGHT, zorder=5)
    if lines:
        body = "\n".join(f"• {line}" for line in lines)
        ax.text(x + 0.15, y + h - 0.55, body, ha="left", va="top",
                fontsize=fs - 1, color=C_PANEL_SUB, zorder=5, linespacing=1.35)


def _dashed_group(ax, x, y, w, h, label=None):
    ax.add_patch(FancyBboxPatch(
        (x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.05",
        facecolor="none", edgecolor=C_DASH, linewidth=1.8, linestyle="--", zorder=3))
    if label:
        ax.text(x + w / 2, y + h - 0.18, label, ha="center", va="top",
                fontsize=8.5, weight="bold", color=C_DASH, zorder=4)


def _arrow(ax, x1, y1, x2, y2, label=None, dashed=False, color=C_ARROW, rad=0.0):
    style = "->" if dashed else "-|>"
    ls = (0, (5, 3)) if dashed else "solid"
    ax.add_patch(FancyArrowPatch(
        (x1, y1), (x2, y2), arrowstyle=style, mutation_scale=14,
        linewidth=1.6, color=color, linestyle=ls,
        connectionstyle=f"arc3,rad={rad}", zorder=2))
    if label:
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        ax.text(mx, my + 0.12, label, ha="center", va="bottom", fontsize=7.5,
                color=C_TEXT_LIGHT if color == C_ARROW else C_TEXT,
                bbox=dict(boxstyle="round,pad=0.15", facecolor=C_HEADER, edgecolor="none", alpha=0.85),
                zorder=6)


def _bq_cylinder(ax, cx, cy, scale=1.0, label="BigQuery", badge=None):
    w, h = 1.1 * scale, 0.22 * scale
    body_h = 1.0 * scale
    ax.add_patch(Ellipse((cx, cy + body_h), w, h, facecolor=C_BQ_TOP, edgecolor="#C87D00", lw=1.2, zorder=5))
    ax.add_patch(Rectangle((cx - w / 2, cy), w, body_h, facecolor=C_BQ_MID, edgecolor="#C87D00", lw=1.2, zorder=4))
    ax.add_patch(Ellipse((cx, cy), w, h, facecolor=C_BQ_BOT, edgecolor="#C87D00", lw=1.2, zorder=5))
    ax.text(cx, cy + body_h / 2, label, ha="center", va="center", fontsize=8 * scale,
            weight="bold", color="#3E2A00", zorder=6)
    if badge:
        ax.add_patch(Circle((cx + w / 2 - 0.05, cy + body_h + 0.05), 0.18 * scale,
                            facecolor="#FFFFFF", edgecolor=C_ACCENT, lw=1.2, zorder=7))
        ax.text(cx + w / 2 - 0.05, cy + body_h + 0.05, badge, ha="center", va="center",
                fontsize=6.5 * scale, weight="bold", color=C_ACCENT, zorder=8)


def _user_icon(ax, cx, cy, scale=1.0):
    ax.add_patch(Circle((cx, cy + 0.35 * scale), 0.18 * scale, facecolor="#B0BEC5", edgecolor="#78909C", zorder=5))
    ax.add_patch(Arc((cx, cy - 0.05), 0.55 * scale, 0.45 * scale, theta1=0, theta2=180,
                     facecolor="#B0BEC5", edgecolor="#78909C", lw=1, zorder=5))


def _browser_icon(ax, x, y, w, h, label):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.01,rounding_size=0.08",
                                facecolor="#ECEFF1", edgecolor="#607D8B", lw=1.2, zorder=5))
    ax.add_patch(Rectangle((x, y + h - 0.18), w, 0.18, facecolor="#CFD8DC", zorder=6))
    ax.add_patch(Circle((x + 0.12, y + h - 0.09), 0.04, facecolor="#EF5350", zorder=7))
    ax.add_patch(Circle((x + 0.22, y + h - 0.09), 0.04, facecolor="#FFCA28", zorder=7))
    ax.add_patch(Circle((x + 0.32, y + h - 0.09), 0.04, facecolor="#66BB6A", zorder=7))
    ax.text(x + w / 2, y + h / 2 - 0.05, label, ha="center", va="center", fontsize=7.5,
            color=C_TEXT, weight="bold", zorder=7)


def _server_icon(ax, x, y, w, h, label, color=C_SPRING):
    for i, shade in enumerate([color, "#5A9E34", "#4A8530"]):
        ax.add_patch(FancyBboxPatch((x, y + i * 0.08), w, h - i * 0.16,
                                    boxstyle="round,pad=0.01,rounding_size=0.04",
                                    facecolor=shade, edgecolor="#33691E", lw=1, zorder=5 + i))
    ax.text(x + w / 2, y + h / 2, label, ha="center", va="center", fontsize=7.5,
            weight="bold", color="white", zorder=8)


def generate_architecture_diagram(output: Path) -> None:
    fig, ax = plt.subplots(figsize=(14, 6.2))
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 6.2)
    ax.axis("off")

    _header(ax, "BigQuery Conversational Agent Architecture", 0.25, 5.45, 13.5)

    # Left — data plane (minimal)
    _panel(ax, 0.3, 0.4, 3.8, 4.75,
           "BigQuery Data Plane",
           "Source data · verified SQL · feedback")

    _bq_cylinder(ax, 2.2, 2.55, scale=1.0, label="BigQuery", badge="SQL")

    _inner_box(ax, 0.55, 3.55, 3.3, 0.95, "Knowledge & agent",
               ["Source tables & verified queries", "Pre-created data agent"])
    _inner_box(ax, 0.55, 0.75, 3.3, 0.85, "Feedback store",
               ["agent_feedback.agent_feedback"])

    # Right — simplified live chat flow (single row)
    _panel(ax, 4.35, 0.4, 9.4, 4.75,
           "Live Chat",
           "Sessions + Memory Bank context · stateless GDA :chat")

    # Main horizontal pipeline
    boxes = [
        (4.75, 2.35, 1.35, 1.05, "Client", "REST / SSE", C_INNER_LIGHT, C_ACCENT),
        (6.35, 2.35, 1.55, 1.05, "Application", "API", "#FFFFFF", "#6DB33F"),
        (8.1, 2.35, 1.75, 1.05, "Vertex AI", "Sessions +\nMemory Bank", C_INNER_LIGHT, C_VERTEX),
        (10.05, 2.35, 1.75, 1.05, "Conversational", "Analytics API", C_INNER_LIGHT, C_ACCENT),
        (11.95, 2.35, 1.55, 1.05, "Data Agent", "NL → SQL", C_INNER_LIGHT, C_GDA),
    ]
    for x, y, w, h, title, sub, face, edge in boxes:
        ax.add_patch(FancyBboxPatch(
            (x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.06",
            facecolor=face, edgecolor=edge, linewidth=1.5, zorder=4))
        ax.text(x + w / 2, y + h * 0.62, title, ha="center", va="center",
                fontsize=8.5, weight="bold", color=C_TEXT, zorder=5)
        ax.text(x + w / 2, y + h * 0.32, sub, ha="center", va="center",
                fontsize=7.5, color="#546E7A", zorder=5)

    _bq_cylinder(ax, 12.75, 1.15, scale=0.72, label="Live\ndata", badge="✓")

    # Primary flow arrows (left to right)
    flow_y = 2.92
    for x1, x2 in [(6.1, 6.35), (7.9, 8.1), (9.85, 10.05), (11.8, 11.95), (13.5, 12.75)]:
        _arrow(ax, x1, flow_y, x2, flow_y)

    # Context load (before chat) — one dashed arrow up to Vertex
    _arrow(ax, 7.12, 2.35, 8.95, 3.45, label="load context", dashed=True, rad=0.25)
    ax.text(8.0, 3.55, "history + memories", ha="center", fontsize=7, color=C_PANEL_SUB, zorder=6)

    # Query BigQuery
    _arrow(ax, 12.72, 2.35, 12.72, 2.05, label="query", color=C_BQ_TOP)

    # Response back to client
    _arrow(ax, 7.12, 2.35, 5.45, 2.35, dashed=True, label="answer", rad=0.2)

    # Feedback path
    _arrow(ax, 7.12, 2.35, 2.2, 1.6, dashed=True, label="feedback", rad=0.18, color=C_MUTED)

    # Legend note
    ax.text(7.05, 0.72,
            "Each turn: load session history & memories → stateless :chat with full context replayed → persist turn to Sessions",
            ha="center", va="center", fontsize=7.5, color=C_PANEL_SUB, style="italic", zorder=6)

    _save(fig, output)


def _seq_step(ax, fx, tx, y, label, dashed=False, highlight=False):
    color = "#E65100" if highlight else C_ARROW
    ls = (0, (5, 3)) if dashed else "solid"
    lw = 2.0 if highlight else 1.4
    ax.annotate(
        "", xy=(tx, y), xytext=(fx, y),
        arrowprops=dict(arrowstyle="-|>" if not dashed else "->", color=color, lw=lw, linestyle=ls),
    )
    bg = "#FFF8E1" if highlight else "white"
    ax.text((fx + tx) / 2, y + 0.1, label, ha="center", va="bottom", fontsize=7,
            color=C_TEXT,
            bbox=dict(boxstyle="round,pad=0.12", facecolor=bg, edgecolor="#E0E0E0", alpha=0.95),
            zorder=6)


def generate_sequence_diagram(output: Path) -> None:
    fig, ax = plt.subplots(figsize=(14, 8.5))
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 8.5)
    ax.axis("off")

    _header(ax, "Conversation Chat Sequence — POST /conversations/{id}/messages", 0.25, 7.75, 13.5)

    _panel(ax, 0.3, 0.35, 13.4, 7.1, "Request lifecycle",
           "Stream response to client first · then persist turn to Sessions · then update Memory Bank")

    actors = [
        (1.3, "Client"),
        (4.0, "Application\nAPI"),
        (7.0, "Vertex\nSessions"),
        (9.8, "Memory\nBank"),
        (12.5, "GDA\n:chat"),
    ]
    top = 6.95
    for x, name in actors:
        ax.add_patch(FancyBboxPatch(
            (x - 0.8, top), 1.6, 0.6, boxstyle="round,pad=0.02,rounding_size=0.06",
            facecolor=C_INNER_LIGHT, edgecolor=C_ACCENT, lw=1.2, zorder=4))
        ax.text(x, top + 0.3, name, ha="center", va="center", fontsize=8, weight="bold", color=C_TEXT, zorder=5)
        ax.plot([x, x], [top, 0.55], color=C_PANEL_EDGE, linewidth=1.3, zorder=2)

    y = 6.35
    # Phase 1 — prepare context
    ax.text(0.45, y + 0.15, "1. Load context", fontsize=8, weight="bold", color=C_ACCENT, zorder=6)
    _seq_step(ax, 1.3, 4.0, y, "POST /messages")
    y -= 0.42
    _seq_step(ax, 4.0, 7.0, y, "GET session events")
    y -= 0.42
    _seq_step(ax, 7.0, 4.0, y, "conversation history")
    y -= 0.42
    _seq_step(ax, 4.0, 9.8, y, "memories:retrieve")
    y -= 0.42
    _seq_step(ax, 9.8, 4.0, y, "user facts")

    # Phase 2 — agent call + stream to client
    y -= 0.55
    ax.text(0.45, y + 0.15, "2. Agent call", fontsize=8, weight="bold", color=C_ACCENT, zorder=6)
    y -= 0.08
    _seq_step(ax, 4.0, 12.5, y, "ChatRequest (history + memories + message)")
    y -= 0.42
    _seq_step(ax, 12.5, 4.0, y, "response stream chunks")
    y -= 0.42
    _seq_step(ax, 4.0, 1.3, y, "SSE / JSON chunks to client")

    # Phase 3 — persist after agent completes
    y -= 0.55
    ax.text(0.45, y + 0.15, "3. Persist turn (after agent completes)", fontsize=8, weight="bold", color="#E65100", zorder=6)
    y -= 0.08
    _seq_step(ax, 4.0, 7.0, y, "appendEvent — user message", highlight=True)
    y -= 0.42
    _seq_step(ax, 4.0, 7.0, y, "appendEvent — agent response", highlight=True)
    y -= 0.42
    _seq_step(ax, 4.0, 9.8, y, "memories:generate (async)", dashed=True)

    note = FancyBboxPatch(
        (0.45, 0.55), 8.5, 0.75, boxstyle="round,pad=0.03,rounding_size=0.06",
        facecolor="#FFF8E1", edgecolor="#F9A825", lw=1.5, zorder=4)
    ax.add_patch(note)
    ax.text(4.7, 0.92, "Session is updated only after the full agent response is received.",
            ha="center", va="center", fontsize=8, color=C_TEXT, zorder=5)

    _save(fig, output)


def generate_capability_diagram(output: Path) -> None:
    fig, ax = plt.subplots(figsize=(16, 6.5))
    ax.set_xlim(0, 16)
    ax.set_ylim(0, 6.5)
    ax.axis("off")

    _header(ax, "Capability → GCP Service → IAM Mapping", 0.3, 5.75, 15.4)
    _panel(ax, 0.35, 0.45, 15.4, 5.05, "Runtime permissions by feature",
           "Grant to service account in GOOGLE_APPLICATION_CREDENTIALS")

    rows = [
        ("Session management", "Vertex AI Agent Engine (Sessions)", "conversationId",
         "roles/aiplatform.user", C_VERTEX),
        ("Memory Bank", "Vertex AI Agent Engine (Memory Bank)", "userId",
         "roles/aiplatform.user", C_VERTEX),
        ("Stateless GDA chat", "geminidataanalytics.googleapis.com", "dataAgentId",
         "dataAgentUser + dataAgentStatelessUser + cloudaicompanion.user", C_ACCENT),
        ("BigQuery queries", "Data Agent → source tables", "verified SQL",
         "bigquery.jobUser + dataViewer on datasets", C_BQ_TOP),
        ("User feedback", "agent_feedback.agent_feedback", "invocationId",
         "bigquery.dataEditor on agent_feedback dataset", C_BQ_MID),
    ]

    y = 4.55
    for cap, svc, scope, role, accent in rows:
        ax.add_patch(FancyBboxPatch(
            (0.65, y), 3.0, 0.75, boxstyle="round,pad=0.02,rounding_size=0.05",
            facecolor=accent, edgecolor="white", lw=1.2, alpha=0.92, zorder=4))
        ax.text(2.15, y + 0.38, cap, ha="center", va="center", fontsize=8.5, weight="bold", color="white", zorder=5)

        ax.add_patch(FancyBboxPatch(
            (4.0, y), 4.8, 0.75, boxstyle="round,pad=0.02,rounding_size=0.05",
            facecolor=C_INNER_LIGHT, edgecolor=C_PANEL_EDGE, lw=1.2, zorder=4))
        ax.text(6.4, y + 0.38, svc, ha="center", va="center", fontsize=8, color=C_TEXT, zorder=5)

        ax.add_patch(FancyBboxPatch(
            (9.1, y), 2.2, 0.75, boxstyle="round,pad=0.02,rounding_size=0.05",
            facecolor=C_INNER, edgecolor="white", lw=1.2, zorder=4))
        ax.text(10.2, y + 0.38, scope, ha="center", va="center", fontsize=7.5, color=C_TEXT_LIGHT, zorder=5)

        ax.add_patch(FancyBboxPatch(
            (11.6, y), 3.8, 0.75, boxstyle="round,pad=0.02,rounding_size=0.05",
            facecolor=C_HEADER, edgecolor=C_PANEL_EDGE, lw=1.2, zorder=4))
        ax.text(13.5, y + 0.38, role, ha="center", va="center", fontsize=7, color=C_PANEL_SUB, zorder=5)

        _arrow(ax, 3.65, y + 0.38, 4.0, y + 0.38, color=C_MUTED)
        _arrow(ax, 8.8, y + 0.38, 9.1, y + 0.38, color=C_MUTED)
        _arrow(ax, 11.3, y + 0.38, 11.6, y + 0.38, color=C_MUTED)
        y -= 0.95

    _save(fig, output)


def generate_all() -> dict[str, Path]:
    paths = {
        "architecture": DIAGRAMS_DIR / "architecture_high_level.png",
        "sequence": DIAGRAMS_DIR / "conversation_sequence.png",
        "capabilities": DIAGRAMS_DIR / "capability_mapping.png",
        "architecture_asset": ASSETS_DIR / "bq-agent-architecture.png",
    }
    generate_architecture_diagram(paths["architecture"])
    generate_architecture_diagram(paths["architecture_asset"])
    generate_sequence_diagram(paths["sequence"])
    generate_capability_diagram(paths["capabilities"])
    return paths


if __name__ == "__main__":
    for name, path in generate_all().items():
        print(f"Wrote {name}: {path}")
