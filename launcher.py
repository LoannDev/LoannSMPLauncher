global INSTALLED_FORGE_VERSION
import hashlib
import io
import json
import logging
import os
import shutil
import sys
import zipfile
from datetime import datetime
from pathlib import Path

import minecraft_launcher_lib as mll
import psutil
import requests
from packaging import version
from PySide6.QtCore import (
    Property,
    QEasingCurve,
    QParallelAnimationGroup,
    QPoint,
    QProcess,
    QPropertyAnimation,
    QRect,
    QSequentialAnimationGroup,
    QSize,
    Qt,
    QThread,
    QTimer,
    QUrl,
    Signal,
    QAbstractAnimation,
)
from PySide6.QtGui import QColor, QDesktopServices, QFont, QTextCursor, QPixmap, QMovie, QPainter, QBrush
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QFrame,
    QGraphicsOpacityEffect,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QStackedWidget,
    QTabWidget,
    QTextEdit,
    QVBoxLayout,
    QWidget,
    QDialog,
)

LOADING_IMAGE_URL = "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/refs/heads/main/icon.ico"
NEWS_URL = "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/refs/heads/main/latestnews.txt"
LAUNCHER_VERSION = "2.1.0"

MINECRAFT_DIR = mll.utils.get_minecraft_directory()
CONFIG_FILE = os.path.join(MINECRAFT_DIR, "launcher_config.json")

def load_config():
    default_config = {
        "base_url": "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/main/",
        "ram_gb": 6,
        "keep_launcher_open": True,
        "discord_url": "https://discord.gg/x3GtCqqXXj",
        "username": "",
        "jvm_args": "-XX:+UseG1GC",
        "custom_res": False,
        "res_w": 1280,
        "res_h": 720,
        "fullscreen": False
    }
    os.makedirs(MINECRAFT_DIR, exist_ok=True)
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r") as f:
                saved = json.load(f)
                default_config.update(saved)
        except:
            pass
    return default_config

def save_config():
    try:
        with open(CONFIG_FILE, "w") as f:
            json.dump(CONFIG, f)
    except:
        pass

CONFIG = load_config()
MODS_DIR = os.path.join(MINECRAFT_DIR, "mods")
SHADERS_DIR = os.path.join(MINECRAFT_DIR, "shaderpacks")
VERSION_FILE = os.path.join(MINECRAFT_DIR, "loannsmp_version.json")
INSTALLED_FORGE_VERSION = None


class ModernCheckBox(QWidget):
    stateChanged = Signal(int)

    def __init__(self, text, parent=None):
        super().__init__(parent)
        self.checked = False
        self.text = text
        self.setFixedHeight(40)
        self.setMinimumWidth(350) # Increased to prevent clipping
        self.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(15)
        
        self.switch_container = QFrame()
        self.switch_container.setFixedSize(40, 22)
        self.switch_container.setStyleSheet("background: #2D2D2D; border-radius: 11px;")
        
        self.switch_circle = QFrame(self.switch_container)
        self.switch_circle.setFixedSize(16, 16)
        self.switch_circle.move(3, 3)
        self.switch_circle.setStyleSheet("background: #A0A0A0; border-radius: 8px;")
        
        layout.addWidget(self.switch_container)
        
        self.label = QLabel(text)
        self.label.setStyleSheet("color: #FFFFFF; font-size: 14px; font-weight: 500;")
        self.label.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft)
        layout.addWidget(self.label)
        layout.addStretch()
        
        self.anim_circle = QPropertyAnimation(self.switch_circle, b"pos")
        self.anim_circle.setDuration(200)
        self.anim_circle.setEasingCurve(QEasingCurve.Type.OutCubic)

    def mousePressEvent(self, event):
        self.toggle()

    def toggle(self):
        self.checked = not self.checked
        if self.checked:
            self.anim_circle.setStartValue(QPoint(3, 3))
            self.anim_circle.setEndValue(QPoint(21, 3))
            self.switch_container.setStyleSheet("background: #FF9500; border-radius: 11px;")
            self.switch_circle.setStyleSheet("background: #FFFFFF; border-radius: 8px;")
        else:
            self.anim_circle.setStartValue(QPoint(21, 3))
            self.anim_circle.setEndValue(QPoint(3, 3))
            self.switch_container.setStyleSheet("background: #2D2D2D; border-radius: 11px;")
            self.switch_circle.setStyleSheet("background: #A0A0A0; border-radius: 8px;")
        self.anim_circle.start()
        self.stateChanged.emit(2 if self.checked else 0)

    def isChecked(self):
        return self.checked

    def setChecked(self, checked):
        if self.checked != checked:
            self.toggle()


class NavButton(QPushButton):
    def __init__(self, icon_text, text, parent=None):
        super().__init__(parent)
        self.setCheckable(True)
        self.setFixedHeight(50)
        self.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        layout = QHBoxLayout(self)
        layout.setContentsMargins(15, 0, 15, 0)
        layout.setSpacing(12)
        
        self.icon_label = QLabel(icon_text)
        self.icon_label.setStyleSheet("font-size: 18px; color: #FFFFFF; border: none; background: transparent; padding: 0;")
        
        self.text_label = QLabel(text)
        self.text_label.setStyleSheet("font-size: 13px; font-weight: 600; color: #FFFFFF; border: none; background: transparent; padding: 0;")
        
        layout.addWidget(self.icon_label)
        layout.addWidget(self.text_label)
        layout.addStretch()
        
        self.update_style()

    def update_style(self):
        self.setStyleSheet("""
            NavButton {
                background: transparent;
                color: #888888;
                border: none;
                border-radius: 12px;
                text-align: left;
                padding: 0 10px;
            }
            NavButton:hover {
                background: rgba(255, 255, 255, 0.05);
                color: #FFFFFF;
            }
            NavButton:checked {
                background: rgba(255, 149, 0, 0.1);
                border: 1px solid rgba(255, 149, 0, 0.5);
                color: #FF9500;
            }
        """)




class ColoredTextEditLogger(logging.Handler):
    def __init__(self, text_edit):
        super().__init__()
        self.text_edit = text_edit

    def emit(self, record):
        try:
            msg = self.format(record)
            color = "#0DBC79"
            if "✅" in msg or "🎉" in msg:
                color = "#38EF7D"
            else:
                if "⚠️" in msg or "🔒" in msg:
                    color = "#FFD93D"
                else:
                    if "❌" in msg:
                        color = "#FF6B6B"
                    else:
                        if "🔍" in msg or "📦" in msg or "🔨" in msg:
                            color = "#FF9500"
            formatted = f'<span style="color: {color};">{msg}</span>'
            self.text_edit.append(formatted)
            self.text_edit.moveCursor(QTextCursor.MoveOperation.End)
        except:
            return None


class UpdateChecker(QThread):
    installation_valid = Signal(bool)
    modpack_unavailable = Signal()

    def run(self):
        global INSTALLED_FORGE_VERSION
        logging.info("🔍 Vérification de l'installation...")
        try:
            resp = requests.get(CONFIG["base_url"] + "modpack.txt", timeout=10)
            remote_url = resp.text.strip()
            if remote_url.lower() == "none":
                logging.info("⚠️ Le modpack n'est pas encore disponible")
                self.modpack_unavailable.emit()
                return
            forge_installed = False
            try:
                forge_version = mll.forge.find_forge_version("1.20.1")
                if forge_version:
                    versions = mll.utils.get_installed_versions(MINECRAFT_DIR)
                    installed_forge = mll.forge.forge_to_installed_version(
                        forge_version
                    )
                    forge_installed = any(
                        (v["id"] == installed_forge for v in versions)
                    )
                    if forge_installed:
                        INSTALLED_FORGE_VERSION = forge_version
                        logging.info(f"✅ Forge {forge_version} détecté")
            except Exception as e:
                logging.warning(f"⚠️ Erreur vérification Forge: {e}")
            try:
                resp = requests.get(CONFIG["base_url"] + "modpack.txt", timeout=10)
                remote_hash = hashlib.md5(resp.text.strip().encode()).hexdigest()
                local_hash = None
                if os.path.exists(VERSION_FILE):
                    try:
                        with open(VERSION_FILE, "r") as f:
                            local_hash = json.load(f).get("modpack_hash")
                    except:
                        pass
                mods_exist = (
                    os.path.exists(MODS_DIR)
                    and len(list(Path(MODS_DIR).glob("*.jar"))) > 0
                )
                if local_hash == remote_hash and mods_exist and forge_installed:
                    logging.info("✅ Installation à jour !")
                    self.installation_valid.emit(True)
                else:
                    if not mods_exist:
                        logging.info("⚠️ Aucun mod installé")
                    elif local_hash != remote_hash:
                        logging.info("⚠️ Mise à jour disponible")
                    elif not forge_installed:
                        logging.info("⚠️ Forge non installé")
                    self.installation_valid.emit(False)
            except Exception as e:
                logging.warning(f"⚠️ Impossible de vérifier la version: {e}")
                self.installation_valid.emit(False)
        except Exception as e:
            logging.warning(f"⚠️ Impossible de vérifier la disponibilité: {e}")
            self.installation_valid.emit(False)


class InstallWorker(QThread):
    progress = Signal(int, str)
    finished = Signal(bool, str)
    log = Signal(str)

    def __init__(self):
        super().__init__()
        self._running = True

    def run(self):
        global INSTALLED_FORGE_VERSION
        self.log.emit(
            "======================================================================"
        )
        self.log.emit("📦 TÉLÉCHARGEMENT DES MODS")
        self.log.emit(
            "======================================================================"
        )
        self.progress.emit(5, "Récupération du lien...")
        self.log.emit("Lecture de modpack.txt...")
        try:
            resp = requests.get(CONFIG["base_url"] + "modpack.txt", timeout=15)
            url = resp.text.strip()
            if url.lower() == "none":
                self.log.emit("❌ Le modpack n'est pas encore sorti")
                self.finished.emit(False, "Modpack pas encore sorti")
                return
            if not url.startswith(("http://", "https://")):
                self.log.emit(f"❌ URL invalide dans modpack.txt: {url}")
                self.finished.emit(False, "URL invalide")
                return
            self.log.emit("✅ URL récupérée avec succès")
            self.progress.emit(10, "Téléchargement...")
            self.log.emit("Téléchargement du modpack...")
            try:
                resp = requests.get(url, stream=True, timeout=120)
                resp.raise_for_status()
                total_size = int(resp.headers.get("content-length", 0))
                if total_size > 0:
                    self.log.emit(f"Taille du fichier: {total_size / 1048576:.2f} MB")
                data = io.BytesIO()
                downloaded = 0
                for chunk in resp.iter_content(8192):
                    if not self._running:
                        return
                    if chunk:
                        data.write(chunk)
                        downloaded += len(chunk)
                self.log.emit(
                    f"✅ Téléchargement terminé: {downloaded / 1048576:.2f} MB"
                )
                self.progress.emit(30, "Extraction...")
                try:
                    os.makedirs(MODS_DIR, exist_ok=True)
                    old_mods = list(Path(MODS_DIR).glob("*.jar"))
                    if old_mods:
                        self.log.emit(
                            f"Suppression de {len(old_mods)} ancien(s) mod(s)..."
                        )
                        for mod in old_mods:
                            try:
                                mod.unlink()
                            except Exception:
                                pass
                    self.log.emit("Extraction du ZIP...")
                    data.seek(0)
                    with zipfile.ZipFile(data) as z:
                        jars = [
                            f
                            for f in z.namelist()
                            if f.endswith(".jar") and (not f.startswith("__MACOSX"))
                        ]
                        if not jars:
                            self.log.emit("❌ Aucun fichier .jar trouvé")
                            self.finished.emit(False, "Aucun mod")
                            return
                        self.log.emit(f"Extraction de {len(jars)} mod(s):")
                        count = 0
                        for jar in jars:
                            try:
                                name = os.path.basename(jar)
                                if name:
                                    content = z.read(jar)
                                    with open(os.path.join(MODS_DIR, name), "wb") as f:
                                        f.write(content)
                                    self.log.emit(f"  ✓ {name}")
                                    count += 1
                            except Exception:
                                continue
                        self.log.emit(f"\n✅ {count} mod(s) installé(s)")
                        try:
                            hash_val = hashlib.md5(url.encode()).hexdigest()
                            with open(VERSION_FILE, "w") as f:
                                json.dump({"modpack_hash": hash_val, "url": url}, f)
                        except Exception:
                            pass
                        self.progress.emit(50, "Recherche Forge...")
                        self.log.emit("\n🔍 RECHERCHE DE FORGE")
                        try:
                            forge_ver = mll.forge.find_forge_version("1.20.1")
                            if not forge_ver:
                                self.finished.emit(False, "Forge introuvable")
                                return
                            self.log.emit(f"✅ Forge: {forge_ver}")
                            versions = mll.utils.get_installed_versions(MINECRAFT_DIR)
                            installed = mll.forge.forge_to_installed_version(forge_ver)
                            if any((v["id"] == installed for v in versions)):
                                INSTALLED_FORGE_VERSION = forge_ver
                                self.log.emit("✅ Forge déjà installé")
                                self.progress.emit(100, "Terminé !")
                                self.finished.emit(True, "Prêt")
                                return
                            self.progress.emit(
                                60,
                                "Installation de Minecraft + Forge (peut prendre bcp de temps)",
                            )
                            self.log.emit(
                                "\n🔨 INSTALLATION DE FORGE (peut prendre bcp de temps, c'est littéralement minecraft)"
                            )

                            def status_cb(s):
                                if self._running:
                                    self.log.emit(s)

                            callback = {
                                "setStatus": status_cb,
                                "setProgress": lambda p: None,
                                "setMax": lambda m: None,
                            }
                            mll.forge.install_forge_version(
                                forge_ver, MINECRAFT_DIR, callback=callback
                            )
                            INSTALLED_FORGE_VERSION = forge_ver
                            self.log.emit("\n🎉 INSTALLATION TERMINÉE")
                            self.progress.emit(100, "Terminé !")
                            self.finished.emit(True, "Prêt")
                        except Exception as e:
                            self.log.emit(f"❌ Erreur: {e}")
                            self.finished.emit(False, "Erreur Forge")
                except Exception as e:
                    self.log.emit(f"❌ Erreur extraction: {e}")
                    self.finished.emit(False, "Erreur extraction")
                    return
            except Exception as e:
                self.log.emit(f"❌ Erreur lors de la lecture de modpack.txt: {e}")
                self.finished.emit(False, "Erreur URL")
                return
        except Exception as e:
            self.log.emit(f"❌ ERREUR: {e}")
            self.finished.emit(False, "Erreur")

    def stop(self):
        self._running = False


class UninstallWorker(QThread):
    finished = Signal(bool, str)
    log = Signal(str)

    def run(self):
        global INSTALLED_FORGE_VERSION
        try:
            self.log.emit("\n🗑️  DÉSINSTALLATION...")
            if os.path.exists(MODS_DIR):
                count = len(list(Path(MODS_DIR).glob("*.jar")))
                shutil.rmtree(MODS_DIR)
                os.makedirs(MODS_DIR)
                self.log.emit(f"✅ {count} mods supprimés")
            versions_dir = os.path.join(MINECRAFT_DIR, "versions")
            if os.path.exists(versions_dir):
                for v in Path(versions_dir).iterdir():
                    if "forge" in v.name.lower():
                        shutil.rmtree(v)
                        self.log.emit(f"✅ {v.name} supprimé")
            if os.path.exists(VERSION_FILE):
                os.remove(VERSION_FILE)
            INSTALLED_FORGE_VERSION = None
            self.log.emit("✅ Terminé")
            self.finished.emit(True, "OK")
        except Exception as e:
            self.log.emit(f"❌ Erreur: {e}")
            self.finished.emit(False, str(e))
            INSTALLED_FORGE_VERSION = None
            self.log.emit("✅ Terminé")
            self.finished.emit(True, "OK")
        except Exception as e:
            self.log.emit(f"❌ Erreur: {e}")
            self.finished.emit(False, str(e))


class ToastNotification(QWidget):
    def __init__(self, parent, message, color="#FF9500"):
        super().__init__(parent)
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.SubWindow)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        
        self.container = QFrame(self)
        self.container.setStyleSheet(f"""
            QFrame {{
                background: #1A1A1A;
                border: 1px solid {color};
                border-radius: 12px;
            }}
        """)
        
        layout = QHBoxLayout(self.container)
        layout.setContentsMargins(15, 8, 15, 8)
        
        label = QLabel(message)
        label.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        label.setStyleSheet(f"color: {color}; background: transparent;")
        layout.addWidget(label)
        
        self.setFixedSize(layout.sizeHint())
        
        # Center horizontally at the bottom
        parent_rect = parent.rect()
        x = (parent_rect.width() - self.width()) // 2
        y = parent_rect.bottom() - 80
        self.move(x, y)
        
        self.opacity = QGraphicsOpacityEffect(self)
        self.setGraphicsEffect(self.opacity)
        
        self.anim = QSequentialAnimationGroup()
        
        fade_in = QPropertyAnimation(self.opacity, b"opacity")
        fade_in.setDuration(400)
        fade_in.setStartValue(0)
        fade_in.setEndValue(1)
        fade_in.setEasingCurve(QEasingCurve.Type.OutCubic)
        
        slide_in = QPropertyAnimation(self, b"pos")
        slide_in.setDuration(400)
        slide_in.setStartValue(QPoint(x, y + 20))
        slide_in.setEndValue(QPoint(x, y))
        slide_in.setEasingCurve(QEasingCurve.Type.OutCubic)
        
        show_group = QParallelAnimationGroup()
        show_group.addAnimation(fade_in)
        show_group.addAnimation(slide_in)
        
        fade_out = QPropertyAnimation(self.opacity, b"opacity")
        fade_out.setDuration(400)
        fade_out.setEndValue(0)
        fade_out.setEasingCurve(QEasingCurve.Type.InCubic)
        
        self.anim.addAnimation(show_group)
        self.anim.addPause(2000)
        self.anim.addAnimation(fade_out)
        self.anim.finished.connect(self.deleteLater)
        self.anim.start()


class CustomMessageBox(QDialog):
    def __init__(self, parent, title, text):
        super().__init__(parent)
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.Dialog)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setFixedSize(320, 180)
        
        self.container = QFrame(self)
        self.container.setFixedSize(320, 180)
        self.container.setStyleSheet("""
            QFrame {
                background: #1A1A1A;
                border: 1px solid #2D2D2D;
                border-radius: 12px;
            }
        """)
        
        layout = QVBoxLayout(self.container)
        layout.setContentsMargins(25, 20, 25, 20)
        
        title_label = QLabel(title)
        title_label.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        title_label.setStyleSheet("color: #FF9500; border: none;")
        layout.addWidget(title_label)
        
        desc_label = QLabel(text)
        desc_label.setFont(QFont("Segoe UI", 10))
        desc_label.setWordWrap(True)
        desc_label.setStyleSheet("color: #E0E0E0; border: none;")
        layout.addWidget(desc_label)
        
        layout.addStretch()
        
        btn_layout = QHBoxLayout()
        btn_layout.setSpacing(10)
        
        self.cancel_btn = QPushButton("Annuler")
        self.cancel_btn.setFixedHeight(34)
        self.cancel_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.cancel_btn.clicked.connect(self.reject)
        self.cancel_btn.setStyleSheet("""
            QPushButton { background: #2D2D2D; color: #888888; border: none; border-radius: 6px; font-weight: bold; }
            QPushButton:hover { background: #3D3D3D; color: white; }
        """)
        
        self.ok_btn = QPushButton("Confirmer")
        self.ok_btn.setFixedHeight(34)
        self.ok_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.ok_btn.clicked.connect(self.accept)
        self.ok_btn.setStyleSheet("""
            QPushButton { background: #DC3545; color: white; border: none; border-radius: 6px; font-weight: bold; }
            QPushButton:hover { background: #C82333; }
        """)
        
        btn_layout.addWidget(self.cancel_btn)
        btn_layout.addWidget(self.ok_btn)
        layout.addLayout(btn_layout)
        
        # Center on parent
        p_rect = parent.geometry()
        self.move(p_rect.center().x() - 160, p_rect.center().y() - 90)


class CrashWindow(QMainWindow):
    def __init__(self, error_msg):
        super().__init__()
        self.setWindowTitle("LoannSMP Launcher - Crash Report")
        self.setFixedSize(500, 350)
        self.setStyleSheet("QMainWindow { background: #121212; } QLabel { color: #E0E0E0; }")
        
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(30, 30, 30, 30)
        layout.setSpacing(15)
        
        icon = QLabel("⚠️")
        icon.setFont(QFont("Segoe UI", 48))
        icon.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(icon)
        
        title = QLabel("Mince, le launcher a crashé !")
        title.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        title.setStyleSheet("color: #FF6B6B;")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)
        
        desc = QLabel("Une erreur inattendue est survenue :")
        desc.setFont(QFont("Segoe UI", 10))
        desc.setStyleSheet("color: #888888;")
        layout.addWidget(desc)
        
        self.error_box = QTextEdit()
        self.error_box.setReadOnly(True)
        self.error_box.setText(error_msg)
        self.error_box.setStyleSheet("""
            QTextEdit {
                background: #1A1A1A;
                color: #FF6B6B;
                border: 1px solid #2D2D2D;
                border-radius: 6px;
                padding: 10px;
                font-family: 'Consolas', monospace;
                font-size: 10px;
            }
        """)
        layout.addWidget(self.error_box)
        
        close_btn = QPushButton("Fermer")
        close_btn.setFixedHeight(36)
        close_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        close_btn.clicked.connect(self.close)
        close_btn.setStyleSheet("""
            QPushButton {
                background: #2D2D2D;
                color: white;
                border: none;
                border-radius: 6px;
            }
            QPushButton:hover { background: #3D3D3D; }
        """)
        layout.addWidget(close_btn)


class ModernProgressBar(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(30)
        self._value = 0
        self._target_value = 0
        self.anim = QPropertyAnimation(self, b"value")
        self.anim.setDuration(300)
        self.anim.setEasingCurve(QEasingCurve.Type.OutCubic)
        
    def get_value(self): return self._value
    def set_value(self, v):
        self._value = v
        self.update()
    value = Property(float, get_value, set_value)

    def setValue(self, v):
        self.anim.stop()
        self.anim.setStartValue(self._value)
        self.anim.setEndValue(v)
        self.anim.start()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        # Background
        bg_rect = QRect(0, 10, self.width(), 10)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor("#1A1A1A"))
        painter.drawRoundedRect(bg_rect, 5, 5)
        
        # Progress with glow
        w = int((self._value / 100) * self.width())
        if w > 4:
            prog_rect = QRect(0, 10, w, 10)
            grad = QColor("#FF9500")
            # Glow effect
            painter.setBrush(QColor(255, 149, 0, 50))
            painter.drawRoundedRect(prog_rect.adjusted(-2, -2, 2, 2), 7, 7)
            
            painter.setBrush(grad)
            painter.drawRoundedRect(prog_rect, 5, 5)
            
        text = f"{int(self._value)}%"
        painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, text)

    def stop(self):
        if hasattr(self, 'anim_group'):
            self.anim_group.stop()
        if hasattr(self, 'bounce_seq'):
            self.bounce_seq.stop()
        self.hide()


class LauncherWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.workers = []
        self.minecraft_process = None
        self.game_running = False
        self.init_ui()
        self.setup_logging()
        
        QTimer.singleShot(0, self.load_header_icon)
        QTimer.singleShot(0, self.fetch_news)
        
        self.check_installation()
        
        self.stats_timer = QTimer()
        self.stats_timer.timeout.connect(self.update_stats)
        self.stats_timer.start(1000)

    def load_header_icon(self):
        try:
            resp = requests.get(LOADING_IMAGE_URL, timeout=5)
            if resp.status_code == 200:
                pixmap = QPixmap()
                pixmap.loadFromData(resp.content)
                self.logo_label.setPixmap(pixmap.scaled(50, 50, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
        except:
            pass


    def fetch_news(self):
        try:
            resp = requests.get(NEWS_URL, timeout=5)
            if resp.status_code == 200:
                self.news_content.setText(resp.text.strip())
                self.show_toast("📰 News synchronisées", "#38EF7D")
        except:
            self.news_content.setText("Impossible de charger les infos.")

    def setup_logging(self):
        handler = ColoredTextEditLogger(self.console)
        handler.setFormatter(logging.Formatter("%(message)s"))
        logging.root.addHandler(handler)
        logging.root.setLevel(logging.INFO)
        logging.info("=== Loann SMP Launcher ===")
        logging.info(f"Démarrage: {datetime.now().strftime('%H:%M:%S')}")
        logging.info(f"Dossier: {MINECRAFT_DIR}\n")

    def show_toast(self, message, color="#FF9500"):
        ToastNotification(self, message, color)

    def startup_animation(self):
        self.opacity = QGraphicsOpacityEffect()
        self.setGraphicsEffect(self.opacity)
        fade = QPropertyAnimation(self.opacity, b"opacity")
        fade.setDuration(500)
        fade.setStartValue(0)
        fade.setEndValue(1)
        fade.setEasingCurve(QEasingCurve.Type.OutCubic)
        geo = self.geometry()
        slide = QPropertyAnimation(self, b"pos")
        slide.setDuration(500)
        slide.setStartValue(QPoint(geo.x(), geo.y() - 50))
        slide.setEndValue(QPoint(geo.x(), geo.y()))
        slide.setEasingCurve(QEasingCurve.Type.OutCubic)
        self.startup_group = QParallelAnimationGroup()
        self.startup_group.addAnimation(fade)
        self.startup_group.addAnimation(slide)
        self.startup_group.start()

    def init_ui(self):
        self.setWindowTitle("LoannSMP Launcher V3")
        self.setFixedSize(850, 550)
        screen = QApplication.primaryScreen().geometry()
        x = (screen.width() - 850) // 2
        y = (screen.height() - 550) // 2
        self.move(x, y)
        self.setStyleSheet("""
            * {
                border: none;
                outline: none;
                background: transparent;
                font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
                margin: 0;
                padding: 0;
            }
            QMainWindow { background: #0A0A0A; }
            QWidget#central { background: #0A0A0A; }
            QLabel { color: #FFFFFF; border: none; background: transparent; }
            QLineEdit { 
                color: #FFFFFF; 
                background: #151515; 
                border-radius: 10px; 
                padding: 12px; 
                border: 1px solid #252525;
            }
            QLineEdit:focus { border: 1px solid #FF9500; }
            QPushButton { outline: none; border: none; background: transparent; }
            QFrame { background: transparent; }
            QStackedWidget { background: transparent; }
            QPushButton { color: #FFFFFF; text-align: center; }
            QScrollArea { border: none; background: transparent; }
            QScrollBar:vertical {
                border: none;
                background: transparent;
                width: 8px;
                margin: 0;
            }
            QScrollBar::handle:vertical {
                background: #2D2D2D;
                min-height: 30px;
                border-radius: 4px;
            }
            QScrollBar::handle:vertical:hover {
                background: #FF9500;
            }
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
                border: none;
                background: transparent;
                height: 0;
            }
            QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {
                background: transparent;
            }
        """)
        central = QWidget()
        central.setObjectName("central")
        self.setCentralWidget(central)
        layout = QHBoxLayout(central)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        
        self.sidebar = QWidget()
        self.sidebar.setFixedWidth(260) # Wider for better title fit
        self.sidebar.setObjectName("sidebar")
        self.sidebar.setStyleSheet("background: #0D0D0D; border: none;")
        sidebar_layout = QVBoxLayout(self.sidebar)
        sidebar_layout.setContentsMargins(20, 30, 20, 30)
        sidebar_layout.setSpacing(10)
        
        brand_container = QWidget()
        brand_container.setFixedHeight(70) # Taller for breathing room
        brand_layout = QHBoxLayout(brand_container)
        brand_layout.setContentsMargins(5, 5, 5, 5)
        brand_layout.setSpacing(15)
        
        self.logo_label = QLabel()
        self.logo_label.setFixedSize(55, 55) # Larger logo
        brand_layout.addWidget(self.logo_label)
        
        title = QLabel("LoannSMP")
        title.setFont(QFont("Segoe UI", 22, QFont.Weight.Bold)) # Slightly smaller font
        title.setMinimumWidth(220) # Better fit for 260px sidebar
        title.setStyleSheet("color: #FF9500; background: transparent; border: none;")
        brand_layout.addWidget(title)
        brand_layout.addStretch()
        sidebar_layout.addWidget(brand_container)
        
        sidebar_layout.addSpacing(30)
        
        self.nav_buttons = []
        tabs = [
            ("🏠", "Accueil"), 
            ("📦", "Mods"),
            ("🌈", "Shaders"),
            ("⚙️", "Options"), 
            ("📊", "Statistiques"), 
            ("📝", "Console")
        ]
        for i, (icon, name) in enumerate(tabs):
            btn = NavButton(icon, name)
            btn.clicked.connect(lambda checked, idx=i: self.switch_page(idx))
            sidebar_layout.addWidget(btn)
            self.nav_buttons.append(btn)
        self.nav_buttons[0].setChecked(True)
        
        sidebar_layout.addStretch()
        layout.addWidget(self.sidebar)
        
        self.content_area = QWidget()
        self.content_layout = QVBoxLayout(self.content_area)
        self.content_layout.setContentsMargins(0, 0, 0, 0)
        self.content_layout.setSpacing(0)
        
        self.stack = QStackedWidget()
        self.stack.addWidget(self.create_launcher_page())
        self.stack.addWidget(self.create_mods_page())
        self.stack.addWidget(self.create_shaders_page())
        self.stack.addWidget(self.create_options_page())
        self.stack.addWidget(self.create_stats_page())
        self.stack.addWidget(self.create_console_page())
        self.content_layout.addWidget(self.stack)
        
        layout.addWidget(self.content_area)
    def create_launcher_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(50, 40, 50, 40)
        layout.setSpacing(25)
        
        news_card = QFrame()
        news_card.setStyleSheet("""
            QFrame {
                background: #111111;
                border-radius: 20px;
                border: 1px solid #1A1A1A;
            }
            QFrame:hover {
                background: #151515;
            }
        """)
        news_layout = QVBoxLayout(news_card)
        news_layout.setContentsMargins(30, 30, 30, 30)
        
        news_header = QHBoxLayout()
        news_title = QLabel("📢  DERNIÈRES INFOS")
        news_title.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        news_title.setStyleSheet("color: #FF9500; background: transparent; border: none;")
        news_header.addWidget(news_title)
        news_header.addStretch()
        news_layout.addLayout(news_header)
        
        news_layout.addSpacing(10)
        
        self.news_content = QLabel("Récupération des dernières nouvelles de LoannSMP...")
        self.news_content.setWordWrap(True)
        self.news_content.setStyleSheet("color: #AAAAAA; font-size: 13px; line-height: 1.5; background: transparent; border: none;")
        news_layout.addWidget(self.news_content)
        
        news_layout.addStretch()
        layout.addWidget(news_card)
        
        layout.addStretch()
        
        bottom_box = QFrame()
        bottom_box.setStyleSheet("background: #0D0D0D; border-radius: 20px; border: 1px solid #1A1A1A;")
        bottom_layout = QVBoxLayout(bottom_box)
        bottom_layout.setContentsMargins(30, 25, 30, 25)
        bottom_layout.setSpacing(15)
        
        user_input_row = QHBoxLayout()
        self.username = QLineEdit()
        self.username.setText(CONFIG.get("username", ""))
        self.username.setPlaceholderText("Entre ton pseudo Minecraft...")
        self.username.setFixedHeight(45)
        self.username.textChanged.connect(self.on_username_changed)
        self.username.setStyleSheet("""
            QLineEdit {
                background: #1A1A1A;
                border: 1px solid #2D2D2D;
                border-radius: 12px;
                padding: 0 15px;
                font-size: 14px;
                color: #FFFFFF;
            }
            QLineEdit:focus { border: 1px solid #333333; background: #1A1A1A; }
        """)
        user_input_row.addWidget(self.username)
        bottom_layout.addLayout(user_input_row)
        
        self.progress = ModernProgressBar()
        bottom_layout.addWidget(self.progress)
        
        self.status = QLabel("Prêt à l'aventure")
        self.status.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.status.setStyleSheet("color: #888888; font-size: 12px; border: none; background: transparent;")
        bottom_layout.addWidget(self.status)
        
        btn_row = QHBoxLayout()
        btn_row.setSpacing(15)
        
        self.install_btn = QPushButton("Mettre à jour")
        self.install_btn.setFixedHeight(50)
        self.install_btn.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self.install_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.install_btn.clicked.connect(self.install)
        self.install_btn.setEnabled(False)
        self.install_btn.setStyleSheet("""
            QPushButton {
                background: #1A1A1A;
                color: #FF9500;
                border: 1px solid #2D2D2D;
                border-radius: 12px;
            }
            QPushButton:hover:enabled { background: #252525; }
        """)
        
        self.launch_btn = QPushButton("REJOINDRE L'AVENTURE")
        self.launch_btn.setFixedHeight(50)
        self.launch_btn.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        self.launch_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.launch_btn.setEnabled(False)
        self.launch_btn.clicked.connect(self.launch)
        self.launch_btn.setStyleSheet("""
            QPushButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #FF9500, stop:1 #FF5E00);
                color: white;
                border: 2px solid transparent;
                border-radius: 12px;
                font-size: 13px;
                font-weight: 800;
                letter-spacing: 1px;
            }
            QPushButton:hover:enabled { 
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #FF9500, stop:1 #FF5E00);
                border: 2px solid #FFFFFF;
            }
            QPushButton:disabled { background: #1A1A1A; color: #444444; }
        """)
        
        btn_row.addWidget(self.install_btn, 1)
        btn_row.addWidget(self.launch_btn, 2)
        bottom_layout.addLayout(btn_row)
        
        layout.addWidget(bottom_box)
        return page

    def create_shaders_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(25)
        
        header = QHBoxLayout()
        title = QLabel("Packs de Shaders")
        title.setFont(QFont("Segoe UI", 20, QFont.Weight.Bold))
        header.addWidget(title)
        header.addStretch()
        
        add_btn = QPushButton("➕ Importer")
        add_btn.setFixedSize(120, 40)
        add_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        add_btn.clicked.connect(self.on_add_shader)
        add_btn.setStyleSheet("""
            QPushButton { background: #1A1A1A; color: #38EF7D; border-radius: 10px; font-weight: bold; border: 1px solid #222; }
            QPushButton:hover { background: #222; border-color: #38EF7D; }
        """)
        header.addWidget(add_btn)
        layout.addLayout(header)
        
        self.shader_search = QLineEdit()
        self.shader_search.setPlaceholderText("🔍 Rechercher un shader...")
        self.shader_search.setFixedHeight(45)
        self.shader_search.textChanged.connect(self.filter_shaders)
        self.shader_search.setStyleSheet("background: #0D0D0D; border: 1px solid #1A1A1A; border-radius: 12px; padding: 0 15px; color: #EEE;")
        layout.addWidget(self.shader_search)
        
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("background: transparent; border: none;")
        
        self.shaders_container = QWidget()
        self.shaders_layout = QVBoxLayout(self.shaders_container)
        self.shaders_layout.setAlignment(Qt.AlignmentFlag.AlignTop)
        self.shaders_layout.setSpacing(12)
        
        scroll.setWidget(self.shaders_container)
        layout.addWidget(scroll)
        
        QTimer.singleShot(100, self.scan_shaders)
        return page

    def scan_shaders(self):
        for i in reversed(range(self.shaders_layout.count())): 
            self.shaders_layout.itemAt(i).widget().setParent(None)
            
        if not os.path.exists(SHADERS_DIR):
            os.makedirs(SHADERS_DIR, exist_ok=True)
            
        shaders = [f for f in os.listdir(SHADERS_DIR) if f.endswith((".zip", ".rar")) or os.path.isdir(os.path.join(SHADERS_DIR, f))]
        for s in shaders:
            self.add_shader_to_list(s)

    def add_shader_to_list(self, shader_name):
        card = QFrame()
        card.setFixedHeight(65)
        card.setObjectName(shader_name)
        card.setStyleSheet("background: #111; border-radius: 12px; border: 1px solid #1A1A1A;")
        c_layout = QHBoxLayout(card)
        c_layout.setContentsMargins(20, 0, 20, 0)
        
        name_label = QLabel(shader_name)
        name_label.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        name_label.setStyleSheet("color: #EEE; border: none; background: transparent;")
        c_layout.addWidget(name_label)
        c_layout.addStretch()
        
        del_btn = QPushButton("🗑️")
        del_btn.setFixedSize(36, 36)
        del_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        del_btn.clicked.connect(lambda: self.delete_shader(shader_name, card))
        del_btn.setStyleSheet("""
            QPushButton { color: #555; background: transparent; border-radius: 18px; font-size: 16px; }
            QPushButton:hover { color: #FF3B30; background: rgba(255, 59, 48, 0.1); }
        """)
        c_layout.addWidget(del_btn)
        
        self.shaders_layout.addWidget(card)

    def on_add_shader(self):
        from PySide6.QtWidgets import QFileDialog
        path, _ = QFileDialog.getOpenFileName(self, "Importer un Shader", "", "Shader Pack (*.zip *.rar)")
        if path:
            filename = os.path.basename(path)
            dest = os.path.join(SHADERS_DIR, filename)
            try:
                shutil.copy2(path, dest)
                self.add_shader_to_list(filename)
                self.show_toast(f"✅ Shader importé : {filename}", "#38EF7D")
            except Exception as e:
                self.show_toast(f"❌ Erreur : {e}", "#FF3B30")

    def delete_shader(self, name, widget):
        msg = CustomMessageBox(self, "Supprimer le shader ?", f"Voulez-vous vraiment supprimer {name} ?")
        if msg.exec():
            try:
                path = os.path.join(SHADERS_DIR, name)
                if os.path.isdir(path):
                    shutil.rmtree(path)
                else:
                    os.remove(path)
                widget.deleteLater()
                self.show_toast("🗑️ Shader supprimé")
            except Exception as e:
                self.show_toast(f"❌ Erreur : {e}", "#FF3B30")

    def filter_shaders(self, text):
        text = text.lower()
        for i in range(self.shaders_layout.count()):
            w = self.shaders_layout.itemAt(i).widget()
            if w:
                w.setVisible(text in w.objectName().lower())

    def create_mods_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(25)
        
        header = QHBoxLayout()
        title = QLabel("Gestion des Mods")
        title.setFont(QFont("Segoe UI", 20, QFont.Weight.Bold))
        header.addWidget(title)
        header.addStretch()
        
        add_btn = QPushButton("➕ Ajouter un Mod")
        add_btn.setFixedSize(140, 40)
        add_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        add_btn.clicked.connect(self.on_add_mod)
        add_btn.setStyleSheet("""
            QPushButton {
                background: #1A1A1A; color: #38EF7D; border: 1px solid #222; border-radius: 10px; font-weight: bold;
            }
            QPushButton:hover { background: #222; border: 1px solid #38EF7D; }
        """)
        header.addWidget(add_btn)
        layout.addLayout(header)
        
        self.mod_search = QLineEdit()
        self.mod_search.setPlaceholderText("🔍 Rechercher parmi vos mods...")
        self.mod_search.setFixedHeight(45)
        self.mod_search.textChanged.connect(self.filter_mods)
        self.mod_search.setStyleSheet("""
            QLineEdit {
                background: #0D0D0D; border: 1px solid #1A1A1A; border-radius: 12px; padding: 0 15px; color: #EEE;
            }
            QLineEdit:focus { border: 1px solid #FF9500; }
        """)
        layout.addWidget(self.mod_search)
        
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("background: transparent; border: none;")
        
        self.mods_container = QWidget()
        self.mods_list_layout = QVBoxLayout(self.mods_container)
        self.mods_list_layout.setAlignment(Qt.AlignmentFlag.AlignTop)
        self.mods_list_layout.setSpacing(12)
        
        scroll.setWidget(self.mods_container)
        layout.addWidget(scroll)
        
        QTimer.singleShot(100, self.scan_mods)
        return page

    def scan_mods(self):
        # Clear existing
        for i in reversed(range(self.mods_list_layout.count())): 
            self.mods_list_layout.itemAt(i).widget().setParent(None)
            
        if not os.path.exists(MODS_DIR):
            os.makedirs(MODS_DIR)
            
        mods = [f for f in os.listdir(MODS_DIR) if f.endswith(".jar")]
        for mod_file in mods:
            self.add_mod_to_list(mod_file)

    def add_mod_to_list(self, mod_file):
        card = QFrame()
        card.setFixedHeight(65)
        card.setObjectName(mod_file)
        card.setStyleSheet("""
            QFrame { 
                background: #111; border-radius: 12px; border: 1px solid #1A1A1A; 
            }
            QFrame:hover {
                background: #151515; border: 1px solid #333;
            }
        """)
        c_layout = QHBoxLayout(card)
        c_layout.setContentsMargins(20, 0, 20, 0)
        
        info = QVBoxLayout()
        info.setSpacing(2)
        name_label = QLabel(mod_file)
        name_label.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        name_label.setStyleSheet("color: #EEE; border: none; background: transparent;")
        info.addWidget(name_label)
        
        size = os.path.getsize(os.path.join(MODS_DIR, mod_file)) / (1024 * 1024)
        sub_label = QLabel(f"Taille: {size:.1f} MB  •  JAR Executable")
        sub_label.setStyleSheet("color: #666; font-size: 10px; border: none; background: transparent;")
        info.addWidget(sub_label)
        c_layout.addLayout(info)
        
        c_layout.addStretch()
        
        del_btn = QPushButton("🗑️")
        del_btn.setFixedSize(36, 36)
        del_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        del_btn.clicked.connect(lambda: self.delete_mod(mod_file, card))
        del_btn.setStyleSheet("""
            QPushButton { color: #555; background: transparent; border-radius: 18px; font-size: 16px; }
            QPushButton:hover { color: #FF3B30; background: rgba(255, 59, 48, 0.1); }
        """)
        c_layout.addWidget(del_btn)
        
        self.mods_list_layout.addWidget(card)

    def on_add_mod(self):
        from PySide6.QtWidgets import QFileDialog
        path, _ = QFileDialog.getOpenFileName(self, "Ajouter un mod", "", "Mod JAR (*.jar)")
        if path:
            filename = os.path.basename(path)
            dest = os.path.join(MODS_DIR, filename)
            try:
                shutil.copy2(path, dest)
                self.add_mod_to_list(filename)
                self.show_toast(f"✅ Mod ajouté : {filename}", "#38EF7D")
            except Exception as e:
                self.show_toast(f"❌ Erreur : {e}", "#FF3B30")

    def delete_mod(self, mod_file, widget):
        msg = CustomMessageBox(self, "Supprimer le mod ?", f"Voulez-vous vraiment supprimer {mod_file} ?")
        if msg.exec():
            try:
                os.remove(os.path.join(MODS_DIR, mod_file))
                widget.deleteLater()
                self.show_toast("🗑️ Mod supprimé", "#FF9500")
            except Exception as e:
                self.show_toast(f"❌ Erreur : {e}", "#FF3B30")

    def filter_mods(self, text):
        text = text.lower()
        for i in range(self.mods_list_layout.count()):
            widget = self.mods_list_layout.itemAt(i).widget()
            if widget:
                visible = text in widget.objectName().lower()
                widget.setVisible(visible)



    def create_options_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(40, 20, 40, 20)
        layout.setSpacing(10)
        
        title = QLabel("Paramètres du Launcher")
        title.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        layout.addWidget(title)
        
        settings_layout = QVBoxLayout()
        settings_layout.setSpacing(8)
        
        # RAM Card (Compact)
        ram_card = QFrame()
        ram_card.setStyleSheet("background: #151515; border-radius: 10px;")
        ram_layout = QVBoxLayout(ram_card)
        ram_layout.setContentsMargins(15, 12, 15, 12)
        ram_layout.setSpacing(10)
        
        ram_header = QLabel("💾 MÉMOIRE RAM")
        ram_header.setStyleSheet("color: #888888; font-weight: bold; font-size: 11px;")
        ram_header.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ram_layout.addWidget(ram_header)
        
        ram_ctrl = QHBoxLayout()
        ram_ctrl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ram_ctrl.setSpacing(15)
        
        btn_style = """
            QPushButton { 
                background: #1A1A1A; 
                color: #FF9500; 
                border-radius: 18px; 
                font-size: 20px; 
                font-weight: bold;
                border: 1px solid #222;
            }
            QPushButton:hover { background: #222; }
            QPushButton:disabled { color: #333; }
        """
        
        self.minus_btn = QPushButton("−")
        self.minus_btn.setFixedSize(36, 36)
        self.minus_btn.clicked.connect(self.decrease_ram)
        self.minus_btn.setStyleSheet(btn_style)
        ram_ctrl.addWidget(self.minus_btn)
        
        self.ram_display = QLabel(f"{CONFIG['ram_gb']} GB")
        self.ram_display.setMinimumWidth(80) 
        self.ram_display.setStyleSheet("font-size: 24px; font-weight: 900; color: #FFFFFF; background: transparent;")
        self.ram_display.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ram_ctrl.addWidget(self.ram_display)
        
        self.plus_btn = QPushButton("+")
        self.plus_btn.setFixedSize(36, 36)
        self.plus_btn.clicked.connect(self.increase_ram)
        self.plus_btn.setStyleSheet(btn_style)
        ram_ctrl.addWidget(self.plus_btn)
        
        ram_layout.addLayout(ram_ctrl)
        settings_layout.addWidget(ram_card)
        
        # Display Card (Compact - Resolution + Fullscreen)
        disp_card = QFrame()
        disp_card.setStyleSheet("background: #151515; border-radius: 10px;")
        disp_layout = QVBoxLayout(disp_card)
        disp_layout.setContentsMargins(15, 10, 15, 10)
        disp_layout.setSpacing(8)
        
        disp_header = QLabel("🖥️ AFFICHAGE")
        disp_header.setStyleSheet("color: #888888; font-weight: bold; font-size: 11px;")
        disp_layout.addWidget(disp_header)
        
        res_row = QHBoxLayout()
        self.custom_res_switch = ModernCheckBox("Résolution")
        self.custom_res_switch.setChecked(CONFIG.get("custom_res", False))
        self.custom_res_switch.stateChanged.connect(lambda s: self.update_adv_config("custom_res", s == 2))
        res_row.addWidget(self.custom_res_switch)
        
        self.res_w_input = QLineEdit()
        self.res_w_input.setFixedWidth(60)
        self.res_w_input.setFixedHeight(30)
        self.res_w_input.setStyleSheet("font-size: 11px; padding: 5px;")
        self.res_w_input.setText(str(CONFIG.get("res_w", 1280)))
        self.res_w_input.textChanged.connect(lambda t: self.update_adv_config("res_w", t))
        
        self.res_h_input = QLineEdit()
        self.res_h_input.setFixedWidth(60)
        self.res_h_input.setFixedHeight(30)
        self.res_h_input.setStyleSheet("font-size: 11px; padding: 5px;")
        self.res_h_input.setText(str(CONFIG.get("res_h", 720)))
        self.res_h_input.textChanged.connect(lambda t: self.update_adv_config("res_h", t))
        
        res_row.addStretch()
        res_row.addWidget(self.res_w_input)
        res_row.addWidget(QLabel("x"))
        res_row.addWidget(self.res_h_input)
        disp_layout.addLayout(res_row)
        
        self.fs_switch = ModernCheckBox("Plein écran")
        self.fs_switch.setChecked(CONFIG.get("fullscreen", False))
        self.fs_switch.stateChanged.connect(lambda s: self.update_adv_config("fullscreen", s == 2))
        disp_layout.addWidget(self.fs_switch)
        
        settings_layout.addWidget(disp_card)
        
        # Prefs (Ultra Compact)
        pref_card = QFrame()
        pref_card.setStyleSheet("background: #151515; border-radius: 10px;")
        pref_layout = QVBoxLayout(pref_card)
        pref_layout.setContentsMargins(15, 10, 15, 10)
        
        self.keep_open_switch = ModernCheckBox("Rester ouvert après lancement")
        self.keep_open_switch.setChecked(CONFIG["keep_launcher_open"])
        self.keep_open_switch.stateChanged.connect(self.toggle_keep_open)
        pref_layout.addWidget(self.keep_open_switch)
        settings_layout.addWidget(pref_card)
        
        # Tools Card (Compact)
        tools_card = QFrame()
        tools_card.setStyleSheet("background: #151515; border-radius: 10px;")
        tools_layout = QHBoxLayout(tools_card)
        tools_layout.setContentsMargins(15, 10, 15, 10)
        tools_layout.addWidget(QLabel("🔧 OUTILS"), 1)
        tools_layout.addWidget(self.create_action_button("📂 Dossier", lambda: os.startfile(MINECRAFT_DIR)))
        tools_layout.addWidget(self.create_action_button("📋 Logs", self.copy_logs))
        settings_layout.addWidget(tools_card)
        
        layout.addLayout(settings_layout)
        layout.addStretch()
        
        self.uninstall_btn = QPushButton("Désinstaller complètement")
        self.uninstall_btn.setFixedHeight(36)
        self.uninstall_btn.clicked.connect(self.uninstall)
        self.uninstall_btn.setStyleSheet("""
            QPushButton { 
                color: #FF3B30; 
                border: 1px solid rgba(255, 59, 48, 0.2); 
                border-radius: 8px; 
                background: rgba(255, 59, 48, 0.05);
                font-size: 11px;
                font-weight: bold;
                margin-top: 5px;
            } 
            QPushButton:hover { background: #FF3B30; color: white; }
        """)
        layout.addWidget(self.uninstall_btn)
        
        QTimer.singleShot(0, self.update_ram_buttons)
        return page

    def update_adv_config(self, key, value):
        if key in ["res_w", "res_h"]:
            try:
                value = int(value)
            except:
                return
        CONFIG[key] = value
        save_config()

    def create_stats_page(self):
        page = QWidget()
        layout = QHBoxLayout(page)
        layout.setContentsMargins(50, 40, 50, 40)
        layout.setSpacing(30)
        
        left = QVBoxLayout()
        self.stats_not_running = QLabel("Minecraft n'est pas lancé.\nLancez le jeu pour voir les stats.")
        self.stats_not_running.setWordWrap(True)
        self.stats_not_running.setStyleSheet("color: #444444; font-size: 13px; font-weight: 600;")
        left.addWidget(self.stats_not_running)
        
        self.stats_container = QWidget()
        s_layout = QVBoxLayout(self.stats_container)
        s_layout.setContentsMargins(0, 0, 0, 0)
        self.cpu_card = self.create_stat_card("💻 UTILISATION CPU", "0%", "#FF9500")
        self.ram_card = self.create_stat_card("💾 RAM CONSOMMÉE", "0 MB", "#38EF7D")
        self.playtime_card = self.create_stat_card("⏱️ TEMPS DE SESSION", "00:00:00", "#5865F2")
        s_layout.addWidget(self.cpu_card)
        s_layout.addWidget(self.ram_card)
        s_layout.addWidget(self.playtime_card)
        self.stats_container.hide()
        left.addWidget(self.stats_container)
        
        layout.addLayout(left, 1)
        
        right_panel = QFrame()
        right_panel.setFixedWidth(300)
        right_panel.setStyleSheet("background: #111; border-radius: 20px;")
        rp_layout = QVBoxLayout(right_panel)
        rp_layout.setContentsMargins(25, 25, 25, 25)
        rp_label = QLabel("📊 Stats MC")
        rp_label.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        rp_label.setStyleSheet("color: #FFFFFF;")
        rp_layout.addWidget(rp_label)
        rp_desc = QLabel("Suivez les performances de votre Minecraft en temps réel.")
        rp_desc.setWordWrap(True)
        rp_desc.setStyleSheet("color: #666; font-size: 11px;")
        rp_layout.addWidget(rp_desc)
        rp_layout.addStretch()
        layout.addWidget(right_panel, 1)
        
        return page

    def create_stat_card(self, title, value, color):
        card = QFrame()
        card.setFixedHeight(70)
        card.setStyleSheet(f"background: #151515; border-radius: 12px;")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(20, 10, 20, 10)
        card_layout.setSpacing(2)
        
        title_label = QLabel(title)
        title_label.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        title_label.setStyleSheet("color: #888888;")
        card_layout.addWidget(title_label)
        
        value_label = QLabel(value)
        value_label.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        value_label.setStyleSheet(f"color: {color};")
        card_layout.addWidget(value_label)
        
        card.value_label = value_label
        return card

    def create_console_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(10, 10, 10, 10)
        self.console = QTextEdit()
        self.console.setReadOnly(True)
        self.console.setStyleSheet("""
            QTextEdit {
                background: #0A0A0A;
                color: #0DBC79;
                border: 1px solid #2D2D2D;
                border-radius: 6px;
                padding: 10px;
                font-family: 'Consolas', monospace;
                font-size: 10px;
            }
        """)
        layout.addWidget(self.console)
        return page

    def create_action_button(self, text, callback):
        btn = QPushButton(text)
        btn.setFixedHeight(32)
        btn.setFont(QFont("Segoe UI", 9, QFont.Weight.Bold))
        btn.setCursor(Qt.CursorShape.PointingHandCursor)
        btn.clicked.connect(callback)
        btn.setStyleSheet("""
            QPushButton {
                background: #222222;
                color: #FFFFFF;
                border-radius: 12px;
                padding: 10px 20px;
                font-size: 11px;
            }
            QPushButton:hover { 
                background: #333333; 
                color: #FF9500;
            }
        """)
        return btn

    def toggle_keep_open(self, state):
        CONFIG["keep_launcher_open"] = state == 2

    def copy_logs(self):
        logs_dir = os.path.join(MINECRAFT_DIR, "logs")
        latest_log = os.path.join(logs_dir, "latest.log")
        if os.path.exists(latest_log):
            try:
                with open(latest_log, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                clipboard = QApplication.clipboard()
                clipboard.setText(content)
                logging.info("✅ Logs copiés dans le presse-papier !")
                self.show_toast("✅ Logs copiés !")
            except Exception as e:
                logging.error(f"❌ Erreur copie logs: {e}")
        else:
            logging.warning("⚠️ Aucun fichier de logs trouvé")

    def open_discord(self):
        QDesktopServices.openUrl(QUrl(CONFIG["discord_url"]))
        logging.info("💬 Ouverture du Discord...")

    def switch_page(self, index):
        if self.stack.currentIndex() == index:
            return
            
        for i, btn in enumerate(self.nav_buttons):
            btn.setChecked(i == index)
            
        old_widget = self.stack.currentWidget()
        new_widget = self.stack.widget(index)
        
        # Opacity Fade transition
        eff = QGraphicsOpacityEffect(new_widget)
        new_widget.setGraphicsEffect(eff)
        
        anim = QPropertyAnimation(eff, b"opacity")
        anim.setDuration(300)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setEasingCurve(QEasingCurve.Type.OutCubic)
        
        if not hasattr(self, '_trans_anims'):
            self._trans_anims = []
        self._trans_anims.append(anim)
        
        self.stack.setCurrentIndex(index)
        anim.start(QAbstractAnimation.DeletionPolicy.DeleteWhenStopped)
        
        page_name = ["Dashboard", "Mods", "Shaders", "Options", "Statistiques", "Console"][index]
        self.show_toast(f"📍 {page_name}")

    def on_username_changed(self, text):
        new_name = text.strip()
        if new_name != CONFIG.get("username", ""):
            CONFIG["username"] = new_name
            save_config()

    def check_installation(self):
        worker = UpdateChecker()
        worker.installation_valid.connect(self.on_check)
        worker.modpack_unavailable.connect(lambda: self.on_check(False))
        worker.start()
        self.workers.append(worker)

    def on_check(self, valid):
        if valid:
            self.status.setText("✅ Tout est à jour !")
            self.status.setStyleSheet("color: #38EF7D; font-weight: 600; border: none; background: transparent;")
            self.launch_btn.setEnabled(True)
            self.install_btn.setText("Déjà à jour")
        else:
            self.status.setText("Mise à jour requise")
            self.status.setStyleSheet("color: #FF9500; font-weight: 600; border: none; background: transparent;")
            self.install_btn.setEnabled(True)

    def install(self):
        self.install_btn.setEnabled(False)
        self.status.setText("Installation des mods...")
        self.console.clear()
        
        worker = InstallWorker()
        worker.progress.connect(self.on_progress)
        worker.finished.connect(self.on_install_done)
        worker.log.connect(lambda msg: self.console.append(msg))
        worker.start()
        self.workers.append(worker)

    def on_install_done(self, success, msg):
        if success:
            self.status.setText("Mods à jour !")
            self.status.setStyleSheet("color: #38EF7D; font-weight: 600; border: none; background: transparent;")
            self.launch_btn.setEnabled(True)
            self.show_toast("🎉 Prêt à jouer !", "#38EF7D")
        else:
            self.status.setText(f"Erreur: {msg}")
            self.status.setStyleSheet("color: #DC3545; border: none; background: transparent;")
            self.install_btn.setEnabled(True)

    def update_stats(self):
        if not self.game_running or not self.minecraft_process:
            self.stats_not_running.show()
            self.stats_container.hide()
            return
        self.stats_not_running.hide()
        self.stats_container.show()
        try:
            mc_pid = self.minecraft_process.processId()
            if mc_pid:
                process = psutil.Process(mc_pid)
                cpu_percent = process.cpu_percent(interval=0.1)
                self.cpu_card.value_label.setText(f"{cpu_percent:.1f}%")
                ram_mb = process.memory_info().rss / 1048576
                self.ram_card.value_label.setText(f"{ram_mb:.0f} MB")
                if self.start_time:
                    elapsed = datetime.now() - self.start_time
                    hours, remainder = divmod(int(elapsed.total_seconds()), 3600)
                    minutes, seconds = divmod(remainder, 60)
                    self.playtime_card.value_label.setText(f"{hours:02d}:{minutes:02d}:{seconds:02d}")
        except:
            pass

    def decrease_ram(self):
        if CONFIG["ram_gb"] > 2:
            CONFIG["ram_gb"] -= 1
            self.ram_display.setText(f"{CONFIG['ram_gb']} GB")
            save_config()
            self.update_ram_buttons()
            self.show_toast(f"💾 RAM: {CONFIG['ram_gb']} GB", "#FF9500")

    def increase_ram(self):
        if CONFIG["ram_gb"] < 16:
            CONFIG["ram_gb"] += 1
            self.ram_display.setText(f"{CONFIG['ram_gb']} GB")
            save_config()
            self.update_ram_buttons()
            self.show_toast(f"💾 RAM: {CONFIG['ram_gb']} GB", "#FF9500")

    def update_ram_buttons(self):
        self.minus_btn.setEnabled(CONFIG["ram_gb"] > 2)
        self.plus_btn.setEnabled(CONFIG["ram_gb"] < 16)

    def on_progress(self, val, text):
        self.progress.setValue(val)
        self.status.setText(text)

    def uninstall(self):
        msg = CustomMessageBox(self, "⚠️ Désinstallation", "Es-tu sûr de vouloir supprimer tous les mods et les versions Forge ?")
        if msg.exec():
            self.uninstall_btn.setEnabled(False)
            worker = UninstallWorker()
            worker.finished.connect(self.on_uninstall_done)
            worker.log.connect(lambda msg: logging.info(msg))
            worker.start()
            self.workers.append(worker)

    def on_uninstall_done(self, success, msg):
        self.uninstall_btn.setEnabled(True)
        if success:
            self.launch_btn.setEnabled(False)
            self.install_btn.setEnabled(True)
            self.install_btn.setText("📦 Installer les mods")
            self.status.setText("Installation requise")
            self.status.setStyleSheet("color: #FF9500; font-weight: 600; border: none; background: transparent;")
            self.show_toast("🗑️ Désinstallation terminée", "#DC3545")

    def launch(self):
        user = self.username.text().strip()
        if not user:
            self.status.setText("⚠️ Pseudo requis")
            return None
        else:
            if not INSTALLED_FORGE_VERSION:
                return None
            else:
                self.launch_btn.setEnabled(False)
                self.username.clearFocus()
        ver = mll.forge.forge_to_installed_version(INSTALLED_FORGE_VERSION)
        ram = CONFIG["ram_gb"]
        logging.info(f"Utilisateur: {user}")
        logging.info(f"RAM: {ram} Go\n")
        jvm_args = [f"-Xmx{ram}G", f"-Xms{ram // 2}G"]
        
        # Add custom JVM args
        extra_args = CONFIG.get("jvm_args", "").strip()
        if extra_args:
            jvm_args.extend(extra_args.split())
            
        opts = {
            "username": user,
            "uuid": "",
            "token": "",
            "jvmArguments": jvm_args,
        }
        
        # Custom Resolution
        if CONFIG.get("custom_res"):
            opts["resolutionWidth"] = str(CONFIG.get("res_w", 1280))
            opts["resolutionHeight"] = str(CONFIG.get("res_h", 720))
            
        # Fullscreen
        if CONFIG.get("fullscreen"):
            opts["gameDirectory"] = MINECRAFT_DIR # Some versions need this for FS context
            # Fullscreen is often handled by adding -f to JVM args or in options.txt, 
            # but mll can pass it in opts sometimes. Let's use standard JVM arg too for safety.
            jvm_args.append("-fullscreen")
        cmd = mll.command.get_minecraft_command(ver, MINECRAFT_DIR, opts)
        self.minecraft_process = QProcess(self)
        self.minecraft_process.readyReadStandardOutput.connect(
            lambda: logging.info(
                bytes(self.minecraft_process.readAllStandardOutput()).decode(
                    "utf-8", errors="ignore"
                )
            )
        )
        self.minecraft_process.finished.connect(self.on_mc_finished)
        self.minecraft_process.start(cmd[0], cmd[1:])
        self.game_running = True
        self.start_time = datetime.now()
        self.status.setText("🎮 En cours...")
        if not CONFIG["keep_launcher_open"]:
            QTimer.singleShot(3000, self.hide)

    def on_mc_finished(self, exit_code, exit_status):
        logging.info("\n🛑 Minecraft fermé")
        self.status.setText("Prêt")
        self.launch_btn.setEnabled(True)
        self.game_running = False
        self.start_time = None
        if not self.isVisible():
            self.show()


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("LoannSMP Launcher")
    try:
        window = LauncherWindow()
        window.show()
        sys.exit(app.exec())
    except Exception as e:
        import traceback
        error_msg = traceback.format_exc()
        crash_win = CrashWindow(error_msg)
        crash_win.show()
        sys.exit(app.exec())


if __name__ == "__main__":
    main()
