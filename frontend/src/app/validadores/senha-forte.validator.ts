import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export interface CriteriosSenha {
  tamanhoMinimo: boolean;
  temMaiuscula: boolean;
  temMinuscula: boolean;
  temNumero: boolean;
  temEspecial: boolean;
}

export function senhaForteValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor: string = control.value || '';

    const criterios: CriteriosSenha = {
      tamanhoMinimo: valor.length >= 8,
      temMaiuscula: /[A-Z]/.test(valor),
      temMinuscula: /[a-z]/.test(valor),
      temNumero: /[0-9]/.test(valor),
      temEspecial: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(valor),
    };

    const valido = Object.values(criterios).every(Boolean);

    return valido ? null : { senhaFraca: criterios };
  };
}

export function obterCriterios(control: AbstractControl | null): CriteriosSenha {
  const erros = control?.errors?.['senhaFraca'];
  if (!erros && control?.value?.length >= 8) {
    return {
      tamanhoMinimo: true,
      temMaiuscula: true,
      temMinuscula: true,
      temNumero: true,
      temEspecial: true,
    };
  }
  return (
    erros ?? {
      tamanhoMinimo: false,
      temMaiuscula: false,
      temMinuscula: false,
      temNumero: false,
      temEspecial: false,
    }
  );
}
