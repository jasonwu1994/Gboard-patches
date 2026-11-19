#!/usr/bin/env python3
"""Populate and validate Spanish settings text. Run --generate only in CI."""
from __future__ import annotations

import argparse
from functools import lru_cache
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TEXT = ROOT / "extensions/extension/src/main/settings-text"
EN = TEXT / "gboard_settings_text.xml"
ES = TEXT / "gboard_settings_text_es.xml"
REPORT = ROOT / "spanish-coverage-report.json"
FMT = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
SPLIT = re.compile(r"(\\n|\n)")
FORMAT_SPLIT = re.compile(r"(%(?:\d+\$)?[a-zA-Z])")
CODE = re.compile(r"^\s*(?:function |return |const |let |var |//|[{};]|https?://)")

# Hand-reviewed terms covering the reported screenshots and common navigation.
REVIEWED = {
    "gboard_patches_navigation_back": "Atrás",
    "gboard_patches_preview_label": "Vista previa",
    "gboard_patches_current_value_label": "Actual",
    "gboard_patches_confirm_action": "Confirmar",
    "gboard_patches_dialog_cancel": "Cancelar",
    "gboard_patches_dialog_save": "Guardar",
    "gboard_patches_dialog_close": "Cerrar",
    "gboard_patches_quick_insert_title": "Inserción rápida",
    "gboard_patches_top_row_title": "Fila superior personalizada",
    "gboard_patches_top_row_summary": "Desliza horizontalmente la fila superior para abrir espacios personalizados de texto y JavaScript.",
    "gboard_patches_top_row_section_behavior": "Comportamiento",
    "gboard_patches_top_row_section_layouts": "Distribuciones compatibles",
    "gboard_patches_top_row_section_slots": "Espacios",
    "gboard_patches_top_row_section_transfer": "Transferencia",
    "gboard_patches_top_row_section_advanced": "Avanzado",
    "gboard_patches_top_row_enabled_title": "Activado",
    "gboard_patches_top_row_export_title": "Exportar ajustes",
    "gboard_patches_top_row_import_title": "Importar ajustes",
    "gboard_patches_top_row_reset_title": "Restablecer espacios",
    "gboard_patches_top_row_slot_title": "Espacio %1$d",
    "gboard_patches_top_row_editor_display_label": "Texto visible",
    "gboard_patches_top_row_editor_commit_label": "Texto que se inserta",
    "gboard_patches_top_row_editor_test_button": "Probar",
    "gboard_patches_top_row_editor_test_running": "Ejecutando…",
    "gboard_patches_top_row_clear_button": "Borrar",
    "gboard_patches_top_row_reset_button": "Restablecer",
    "gboard_patches_clipboard_show_sensitive_content_title": "Mostrar contenido confidencial del portapapeles",
    "gboard_patches_clipboard_show_sensitive_content_summary": "Muestra el texto original en lugar de puntos para los elementos marcados como confidenciales. Esto puede mostrar contraseñas y datos privados en pantalla.",
    "gboard_patches_clipboard_card_preview_limit_title": "Mostrar solo los primeros 1000 caracteres de las tarjetas",
    "gboard_patches_clipboard_card_preview_limit_summary": "Limita el texto mostrado en cada tarjeta del portapapeles a los primeros 1000 caracteres para evitar interrupciones al desplazarse.",
    "gboard_patches_custom_content_max_lines_dialog_title": "Líneas de vista previa personalizadas",
    "gboard_patches_custom_content_max_lines_dialog_hint": "Líneas",
    "gboard_patches_custom_ttl_dialog_title": "Tiempo de conservación personalizado",
    "gboard_patches_custom_ttl_dialog_hint": "Minutos",
    "gboard_patches_custom_max_count_dialog_title": "Cantidad máxima personalizada",
    "gboard_patches_custom_max_count_dialog_hint": "Cantidad",
    "gboard_patches_manual_incognito_title": "Modo incógnito",
    "gboard_patches_manual_incognito_summary": "Añade un botón de incógnito a la barra de herramientas y configura el portapapeles y el dictado por voz.",
    "gboard_patches_long_press_quick_actions_title": "Accesos de edición mediante pulsación prolongada",
    "gboard_patches_long_press_quick_actions_summary": "Mantén pulsadas las teclas indicadas para acceder a Seleccionar todo, Deshacer, Copiar, Cortar, Pegar y Rehacer.",
    "gboard_patches_long_press_quick_actions_action_select_all": "Seleccionar todo",
    "gboard_patches_long_press_quick_actions_action_undo": "Deshacer",
    "gboard_patches_long_press_quick_actions_action_copy": "Copiar",
    "gboard_patches_long_press_quick_actions_action_cut": "Cortar",
    "gboard_patches_long_press_quick_actions_action_paste": "Pegar",
    "gboard_patches_long_press_quick_actions_action_redo": "Rehacer",
    "gboard_patches_ocr_title": "OCR / Escanear texto",
    "gboard_patches_ocr_summary": "Controla el motor utilizado para reconocer texto.",
    "gboard_patches_ocr_engine_title": "Motor de reconocimiento",
    "gboard_patches_ocr_status_installed": "Instalado",
    "gboard_patches_ocr_status_not_installed": "No instalado. Toca para descargar.",
    "gboard_patches_ocr_status_checking": "Comprobando la instalación…",
    "gboard_patches_ocr_status_download_failed": "Error en la descarga. Toca para reintentar.",
    "gboard_patches_ocr_status_current": "Actual",
    "gboard_patches_cursor_trackpad_title": "Activar modo panel táctil para el cursor",
    "gboard_patches_cursor_trackpad_summary": "Mantén pulsada la barra espaciadora para mover libremente el cursor con un panel táctil y un modo de bloqueo.",
    "gboard_patches_floating_web_search_title": "Búsqueda web flotante",
    "gboard_patches_floating_web_search_summary": "Abre una página web flotante desde Gboard. El navegador conserva sus cookies, sesiones, barra de herramientas y compatibilidad.",
    "gboard_patches_swipe_dismiss_title": "Deslizar hacia abajo para cerrar el teclado",
    "gboard_patches_swipe_dismiss_summary": "Desliza hacia abajo desde las sugerencias o la barra de herramientas para cerrar el teclado.",
    "gboard_patches_keypress_haptics_title": "Intensidad de vibración al pulsar teclas",
    "gboard_patches_shift_delete_title": "Mayús + Retroceso: borrar hacia delante",
    "gboard_patches_shift_delete_summary": "Mantén pulsada Mayús y presiona Retroceso para borrar el carácter que sigue al cursor.",
    "gboard_patches_backup_restore_title": "Copia de seguridad y restauración",
    "gboard_patches_backup_restore_summary": "Exporta o restaura todos los ajustes de los parches.",
    "gboard_patches_symbol_footer_title": "Orden de pestañas de emojis, stickers y GIF",
    "gboard_patches_symbol_footer_summary": "Reordena las pestañas inferiores del panel de emojis, stickers y GIF de Gboard.",
    "gboard_patches_frosted_glass_title": "Cristal esmerilado",
    "gboard_patches_frosted_glass_summary": "Aplica desenfoque al fondo del teclado en tiempo real.",
    "gboard_patches_rounded_keyboard_title": "Panel del teclado con esquinas redondeadas",
    "gboard_patches_rounded_keyboard_summary": "Personaliza las esquinas redondeadas del teclado y los radios superiores e inferiores.",
}


def read_sources():
    source = list(ET.parse(EN).getroot())
    overlay = list(ET.parse(ES).getroot())
    names = [x.attrib["name"] for x in source]
    assert len(names) == len(set(names)), "Duplicate English keys"
    keys = [x.attrib["name"] for x in overlay]
    assert len(keys) == len(set(keys)), "Duplicate Spanish keys"
    return source, {x.attrib["name"]: x for x in overlay}


def english_text(entry, plural=None):
    target = entry if plural is None else next(
        x for x in entry if x.attrib["quantity"] == plural
    )
    value = target.find("translation[@locale='en']")
    if value is None:
        raise ValueError("Missing English translation")
    return value.text or ""


def fmt(text):
    return sorted(FMT.findall(text))


def coverage(source, spanish):
    source_by_name = {x.attrib["name"]: x for x in source}
    missing = sorted(source_by_name.keys() - spanish.keys())
    unknown = sorted(spanish.keys() - source_by_name.keys())
    errors = []
    unchanged = []
    for name, dst in spanish.items():
        src = source_by_name.get(name)
        if src is None:
            continue
        if dst.tag != src.tag:
            errors.append(f"{name}: different resource type")
            continue
        if src.tag == "string":
            original, target = english_text(src), dst.text or ""
            if fmt(original) != fmt(target):
                errors.append(f"{name}: changed format specifiers")
            if original == target and re.search(r"[a-zA-Z]{4,}", original):
                unchanged.append(name)
        else:
            en_items = {item.attrib["quantity"]: english_text(src, item.attrib["quantity"])
                        for item in src}
            es_items = {item.attrib["quantity"]: item.text or "" for item in dst}
            if set(en_items) != set(es_items):
                errors.append(f"{name}: plural quantity mismatch")
            for quantity, translated in es_items.items():
                original = en_items.get(quantity, "")
                if fmt(original) != fmt(translated):
                    errors.append(f"{name}[{quantity}]: changed format specifiers")
                if original == translated and re.search(r"[a-zA-Z]{4,}", original):
                    unchanged.append(f"{name}[{quantity}]")
    return dict(source_entries=len(source_by_name), spanish_entries=len(spanish),
                missing=missing, unknown=unknown, errors=errors,
                unchanged_english_or_technical=unchanged)


def make_translator():
    import argostranslate.package as package
    from argostranslate import translate
    package.update_package_index()
    model = next((p for p in package.get_available_packages()
                  if p.from_code == "en" and p.to_code == "es"), None)
    if model is None:
        raise RuntimeError("English to Spanish Argos model unavailable")
    package.install_from_path(model.download())
    src = translate.get_language_from_code("en")
    dst = translate.get_language_from_code("es")
    if src is None or dst is None:
        raise RuntimeError("Argos installed model missing languages")
    return src.get_translation(dst)


def translate_with(route):
    @lru_cache(maxsize=4096)
    def fragment(value):
        if not re.search(r"[A-Za-z]", value) or CODE.match(value):
            return value
        leading = re.match(r"^\s*", value).group()
        trailing = re.search(r"\s*$", value).group()
        core = value[len(leading):len(value) - len(trailing) if trailing else len(value)]
        if not core:
            return value
        result = route.translate(core.replace("\\'", "'"))
        if not result or not result.strip():
            raise RuntimeError(f"Empty translation for {core!r}")
        return leading + result.strip() + trailing

    def convert(value):
        if not value.strip():
            return value
        translated_parts = []
        for line in SPLIT.split(value):
            if line in ("\\n", "\n"):
                translated_parts.append(line)
                continue
            for part in FORMAT_SPLIT.split(line):
                if not part:
                    continue
                if FORMAT_SPLIT.fullmatch(part):
                    translated_parts.append(part)
                else:
                    translated_parts.append(fragment(part))
        result = "".join(translated_parts)
        # Apostrophes must be escaped for Android resource compilation.
        result = result.replace("\\'", "'").replace("'", "\\'")
        if fmt(value) != fmt(result):
            raise ValueError("Format specifiers changed during translation")
        return result
    return convert


def generate(source, previous):
    missing = [x for x in source if x.attrib["name"] not in previous]
    invalid_reviewed = sorted(REVIEWED.keys() -
                             {x.attrib["name"] for x in source})
    if invalid_reviewed:
        raise ValueError(f"Invalid reviewed keys: {invalid_reviewed}")
    print(f"Missing Spanish keys: {len(missing)}", flush=True)
    translate = translate_with(make_translator()) if missing else None
    result = ET.Element("settings-text", {"locale": "es"})
    for index, src in enumerate(source, 1):
        name = src.attrib["name"]
        if name in previous:
            result.append(previous[name])
            continue
        dest = ET.SubElement(result, src.tag, {"name": name})
        if src.tag == "string":
            original = english_text(src)
            text = REVIEWED.get(name)
            if text is None:
                text = translate(original)
            if fmt(original) != fmt(text):
                raise ValueError(f"Bad reviewed placeholders: {name}")
            dest.text = text
        elif src.tag == "plurals":
            for child in src:
                quantity = child.attrib["quantity"]
                original = english_text(src, quantity)
                translated = translate(original)
                if fmt(original) != fmt(translated):
                    raise ValueError(f"Bad plural placeholders: {name}")
                ET.SubElement(dest, "item", {"quantity": quantity}).text = translated
        else:
            raise ValueError(f"Unsupported entry: {name}")
        if index % 100 == 0:
            print(f"Translated {index}/{len(source)} entries", flush=True)
    check = coverage(source, {item.attrib["name"]: item for item in result})
    if check["missing"] or check["unknown"] or check["errors"]:
        raise RuntimeError(f"Invalid resulting translation: {check}")
    ET.indent(result, space="    ")
    ES.write_text('<?xml version="1.0" encoding="utf-8"?>\n'
                  '<!-- Spanish reviewed UI and offline-generated technical descriptions. -->\n'
                  + ET.tostring(result, encoding="unicode") + "\n", encoding="utf-8")
    REPORT.write_text(json.dumps(check, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")
    return check


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.generate == args.check:
        parser.error("Select exactly --generate or --check")
    source, spanish = read_sources()
    if args.generate:
        result = generate(source, spanish)
    else:
        result = coverage(source, spanish)
    print(json.dumps({key: len(value) if isinstance(value, list) else value
                      for key, value in result.items()}, ensure_ascii=False))
    if result["missing"] or result["unknown"] or result["errors"]:
        raise SystemExit("Spanish translation coverage is incomplete")


if __name__ == "__main__":
    main()
