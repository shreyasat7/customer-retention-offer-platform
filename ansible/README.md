# Ansible — provisioning the CROP application node

## Layout

```
ansible/
├── ansible.cfg              defaults: inventory, roles path, no privilege escalation
├── inventory.ini            the target node
├── group_vars/all.yml       the whole configuration specification
├── site.yml                 provision + deploy
├── rollback.yml             roll back to a previously published image tag
└── roles/
    ├── prerequisites/       packages, runtime, directories, files, volume, ports
    ├── app_container/       pull image, run container, health check
    └── nginx_proxy/         reverse proxy on :8081
```

## Usage

```bash
cd ansible

ansible-playbook site.yml                       # provision and deploy :latest
ansible-playbook site.yml -e crop_image_tag=11  # deploy a specific build
ansible-playbook site.yml --check --diff        # dry run, show diffs
ansible-playbook site.yml --tags proxy          # one concern only
ansible-playbook rollback.yml -e rollback_to=11 # roll back

ansible-inventory --graph                       # show the inventory
ansible crop_app -m ping                        # reachability
```

## Idempotency

Re-running `site.yml` against an already-provisioned node must report **`changed=0`**.
Every task is either declarative (`file`, `copy`, `template`, `docker_container`) or an
explicit read marked `changed_when: false`. The one shell task — starting nginx — reports
changed only when it actually started the service.

## What is deliberately not managed here

- **Docker Engine itself.** Docker Desktop is a GUI application on macOS; the playbook
  *verifies* the daemon is reachable and fails fast if it is not, rather than pretending
  it can install it.
- **The data volume's contents.** The volume is created if missing and never otherwise
  touched. That is what makes a rollback non-destructive.
