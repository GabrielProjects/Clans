# Clans

Plugin Paper 1.21 per la gestione di clan con sistema di territori, persistenza MariaDB e integrazione WorldGuard.

Repository: https://github.com/GabrielProjects/Clans

## Funzionalità

- Creazione e gestione clan con ruoli (Leader, Officer, Member)
- Inviti, kick con conferma, promote/demote
- Chat privata del clan (`/clan chat`)
- Claim/unclaim per chunk con protezione WorldGuard
- Home del clan con cooldown e warmup
- Mappa claim in chat (`/clan map`) con bussola N/E/S/W
- Tag del clan in tab list e chat globale
- Notifiche action bar all'ingresso/uscita dalle zone claimate
- PlaceholderAPI (opzionale)

## Requisiti

- **Paper** 1.21.x
- **Java** 21
- **MariaDB** (o MySQL compatibile)
- **WorldGuard** 7.x + WorldEdit
- **PlaceholderAPI** (opzionale)

## Installazione

1. Crea il database MariaDB (oppure usa `scripts/setup-database.sql`):

```sql
CREATE DATABASE clans CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'clans'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON clans.* TO 'clans'@'localhost';
FLUSH PRIVILEGES;
```

2. Configura `plugins/Clans/config.yml` con le credenziali del database.
3. Copia `Clans-1.0.0.jar` nella cartella `plugins/`.
4. Riavvia il server (**non usare `/reload`** con WorldGuard attivo).

Le regioni WorldGuard dei claim vengono create e sincronizzate automaticamente dal plugin all'avvio.

## Comandi

| Comando | Descrizione | Permesso |
|---------|-------------|----------|
| `/clan create <nome> <tag>` | Crea un nuovo clan | `clans.create` |
| `/clan disband` | Scioglie il tuo clan (solo leader) | `clans.use` |
| `/clan disband <clan>` | Scioglie il clan solo se è il tuo | `clans.use` |
| `/clan invite <player>` | Invita un giocatore | `clans.invite` |
| `/clan accept` / `/clan deny` | Accetta o rifiuta un invito | `clans.use` |
| `/clan kick <player> [confirm]` | Espelli un membro (conferma richiesta) | `clans.kick` |
| `/clan promote <player>` | Promuove a Officer | `clans.promote` |
| `/clan demote <player>` | Retrocede a Membro | `clans.demote` |
| `/clan chat <messaggio>` | Chat privata del clan | `clans.use` |
| `/clan claim` | Reclama il chunk corrente | `clans.claim` |
| `/clan unclaim` | Libera il chunk corrente | `clans.claim` |
| `/clan map` | Mappa claim 5×5 centrata sul giocatore | `clans.use` |
| `/clan sethome` | Imposta la home del clan | `clans.sethome` |
| `/clan home` | Teletrasporto alla home | `clans.use` |
| `/clan info [clan]` | Mostra informazioni | `clans.use` |
| `/clan help` | Elenco comandi | `clans.use` |

## Ruoli

| Ruolo | Permessi |
|-------|----------|
| **Leader** | Tutte le azioni, incluso disband e gestione ruoli |
| **Officer** | Invite, kick (solo membri), claim/unclaim, sethome |
| **Member** | Chat, home, info, map |

## Territori

- Ogni claim corrisponde a un chunk e crea una regione WorldGuard dedicata.
- I **membri** del clan possono costruire nel proprio territorio.
- I **non-membri** non possono rompere o piazzare blocchi nelle zone claimate da altri clan.
- All'attraversamento dei confini compare un messaggio in action bar (es. entrata/uscita zona).

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
  flags:
    build: deny
    block-break: deny
    block-place: deny

chat:
  format: "&7[ClanChat] &8[&6{tag}&8] &7<{player}>&8: &f{message}"

display:
  enabled: true
  claim-zone-notifications: true
  tab-format: "&8[&6{tag}&8] &f{player}"
  global-chat-format: "&8[&6{tag}&8] &7{player}&8: &f{message}"

map:
  size: 5
  own-clan-char: "#"
  own-clan-color: "&a"
  unclaimed-char: "/"
  enemy-chars: ["#", "@", "$", "%"]

home:
  cooldown-seconds: 60
  warmup-seconds: 3
```

I messaggi sono personalizzabili in `plugins/Clans/messages.yml`.

## Build

Con Maven:

```bash
mvn clean package
```

Su Windows (senza Maven), con JDK 21 e le dipendenze in `.tools/lib`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-plugin.ps1
```

Il JAR sarà disponibile in `target/Clans-1.0.0.jar`.

## Sviluppo locale (Windows)

Script utili nella cartella `scripts/`:

| Script | Descrizione |
|--------|-------------|
| `setup-database.sql` | Crea database e utente di test |
| `setup-test-server.ps1` | Scarica Paper e i plugin per il server di test |
| `build-plugin.ps1` | Compila e copia il JAR nel server locale |
| `start-mariadb.ps1` | Avvia MariaDB senza servizio Windows |
| `start-test-server.ps1` | Avvia il server Paper locale |
| `start-local.bat` | Avvia MariaDB + server in sequenza |
| `setup-test-claims.ps1` | Crea clan di test con claim su chunk (0,0) e (0,1) |

## Licenza

MIT
