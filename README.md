# Clans

Plugin Paper 1.21 per la gestione di clan con sistema di territori, persistenza MariaDB e integrazione WorldGuard.

Repository: https://github.com/GabrielProjects/Clans

## Requisiti

- **Paper** 1.21.x
- **Java** 21
- **MariaDB** (o MySQL compatibile)
- **WorldGuard** 7.x + WorldEdit
- **PlaceholderAPI** (opzionale)

## Installazione

1. Crea il database MariaDB:

```sql
CREATE DATABASE clans CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'clans'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON clans.* TO 'clans'@'localhost';
FLUSH PRIVILEGES;
```

2. Configura `plugins/Clans/config.yml` con le credenziali del database.
3. Copia `Clans-1.0.0.jar` nella cartella `plugins/`.
4. Riavvia il server.

## Comandi

| Comando | Descrizione | Permesso |
|---------|-------------|----------|
| `/clan create <nome> <tag>` | Crea un nuovo clan | `clans.create` |
| `/clan disband` | Scioglie il clan (solo leader) | `clans.use` |
| `/clan invite <player>` | Invita un giocatore | `clans.invite` |
| `/clan accept` / `/clan deny` | Accetta o rifiuta un invito | `clans.use` |
| `/clan kick <player> [confirm]` | Espelli un membro (conferma richiesta) | `clans.kick` |
| `/clan promote <player>` | Promuove a Officer | `clans.promote` |
| `/clan demote <player>` | Retrocede a Membro | `clans.demote` |
| `/clan chat <messaggio>` | Chat privata del clan | `clans.use` |
| `/clan claim` | Reclama il chunk corrente | `clans.claim` |
| `/clan unclaim` | Libera il chunk corrente | `clans.claim` |
| `/clan sethome` | Imposta la home del clan | `clans.sethome` |
| `/clan home` | Teletrasporto alla home | `clans.use` |
| `/clan info [clan]` | Mostra informazioni | `clans.use` |

## Ruoli

| Ruolo | Permessi |
|-------|----------|
| **Leader** | Tutte le azioni, incluso disband e gestione ruoli |
| **Officer** | Invite, kick (solo membri), claim/unclaim, sethome |
| **Member** | Chat, home, info |

## PlaceholderAPI

| Placeholder | Descrizione |
|-------------|-------------|
| `%clans_player_clan%` | Nome del clan del giocatore |
| `%clans_player_tag%` | Tag del clan |
| `%clans_player_role%` | Ruolo nel clan |
| `%clans_clan_members_online%` | Membri online del clan |

## Configurazione

Esempio `config.yml`:

```yaml
database:
  host: localhost
  port: 3306
  database: clans
  username: clans
  password: "password"

claims:
  max-per-clan: 10
  min-y: -64
  max-y: 320
  allowed-worlds: []

home:
  cooldown-seconds: 60
  warmup-seconds: 3
```

## Build

```bash
mvn clean package
```

Il JAR sarà disponibile in `target/Clans-1.0.0.jar`.

## Licenza

MIT
